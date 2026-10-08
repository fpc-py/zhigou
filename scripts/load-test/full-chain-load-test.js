// ============================================================
// 智购 · 全链路压测脚本（k6）
// 覆盖黄金路径：登录 → 商品列表 → 商品详情 → 加购 → 下单 → 支付
//               → 我的订单 → 售后列表
// 运行（Windows，项目根）：
//   docker run --rm -v ${PWD}/scripts/load-test:/scripts -e BASE_URL=http://host.docker.internal:3000 \
//     grafana/k6 run /scripts/full-chain-load-test.js
// 预置：redis SET "auth:sms:13800138001" "123456" EX 300
// ============================================================
import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";
const PHONE = __ENV.PHONE || "13800138001";
const CODE = "123456";

// QUICK=1 时用 20s/50VU 冒烟验证（不跑全量阶梯）
const QUICK = __ENV.QUICK === "1";
const STAGES = QUICK
  ? [{ duration: "20s", target: 50 }]
  : [
      { duration: "1m", target: 100 },
      { duration: "1m", target: 500 },
      { duration: "1m", target: 1000 },
      { duration: "1m", target: 2000 },
      { duration: "30s", target: 2000 },
    ];

export const options = {
  // 阶梯压测：100 → 500 → 1000 → 2000（各 1 分钟）；QUICK=1 冒烟 20s/50VU
  thresholds: {
    http_req_failed: ["rate<0.01"], // 错误率 < 1%
    http_req_duration: ["p(95)<1500"], // P95 < 1.5s（全链路含写操作）
  },
  // 场景：读为主（80%），写链路（20%）
  scenarios: {
    full_chain: {
      executor: "ramping-vus",
      stages: STAGES,
    },
  },
};

// 全局登录一次（验证码预置），VU 共享 token（读链路为主；写链路 userId 相同）
export function setup() {
  const loginRes = http.post(
    `${BASE}/auth/login`,
    JSON.stringify({ phone: PHONE, code: CODE }),
    { headers: { "Content-Type": "application/json" } }
  );
  console.log("LOGIN status=" + loginRes.status + " body=" + loginRes.body.slice(0, 200));
  check(loginRes, { "login 2xx": (r) => r.status === 200 || r.status === 201 });
  const token = loginRes.json("data.accessToken") || loginRes.json("accessToken");
  if (!token) {
    throw new Error("login failed: " + loginRes.status + " " + loginRes.body);
  }
  return { token, headers: { Authorization: `Bearer ${token}` } };
}

// 读商品列表，取第一个 spuId（无 token）
function fetchFirstSpu() {
  const res = http.get(`${BASE}/product/page?page=1&size=5`);
  check(res, { "product/page 200": (r) => r.status === 200 });
  const list = res.json("data.records") || res.json("data.items") || [];
  return list.length ? (list[0].spuId || list[0].id) : null;
}

// 加购 → 下单 → 支付创建（返回 skuId）
function buyFlow(headers) {
  // 1) 商品列表拿 skuId
  const page = http.get(`${BASE}/product/page?page=1&size=5`, { headers });
  const list = page.json("data.records") || page.json("data.items") || [];
  const sku = list[0]?.skus?.[0]?.skuId || list[0]?.skuId;
  if (!sku) return;

  // 2) 加购
  const cart = http.post(
    `${BASE}/cart/add`,
    JSON.stringify({ skuId: sku, count: 1 }),
    { headers: { "Content-Type": "application/json", ...headers } }
  );
  if (cart.status !== 200) return;

  // 3) 下单
  const order = http.post(
    `${BASE}/order/create`,
    JSON.stringify({ skuId: sku, count: 1 }),
    { headers: { "Content-Type": "application/json", ...headers } }
  );
  if (order.status !== 200) return;
  const orderNo = order.json("data.orderNo") || order.json("data.orderId");

  // 4) 创建支付单（不触发 mock-pay，避免高频真实回调；orderNo 不存在则跳过）
  if (orderNo) {
    const pay = http.post(
      `${BASE}/payment/create`,
      JSON.stringify({ orderNo }),
      { headers: { "Content-Type": "application/json", ...headers } }
    );
    check(pay, { "payment/create 200": (r) => r.status === 200 });
  }
}

export default function (data) {
  const headers = data.headers;

  // 读链路（80%）
  const page = http.get(`${BASE}/product/page?page=1&size=20`, { headers });
  check(page, { "product/page 200": (r) => r.status === 200 });

  const list = page.json("data.records") || page.json("data.items") || [];
  if (list.length) {
    const spuId = list[0].spuId || list[0].id;
    const detail = http.get(`${BASE}/product/${spuId}/detail`, { headers });
    check(detail, { "product/detail 200": (r) => r.status === 200 });
  }

  const mine = http.get(`${BASE}/order/mine`, { headers });
  check(mine, { "order/mine 200": (r) => r.status === 200 });

  const aftersale = http.get(`${BASE}/aftersale/mine`, { headers });
  check(aftersale, { "aftersale/mine 200": (r) => r.status === 200 });

  // 写链路（20%）
  if (__ITER % 5 === 0) {
    buyFlow(headers);
  }

  sleep(0.5);
}
