// ============================================================
// 智购 · 全链路压测脚本（k6）
//
// 模拟用户路径: 浏览商品 → 加购 → 下单 → 支付（沙箱）
// 阶梯加压: 100 → 500 → 1000 → 2000 并发
//
// 运行:
//   k6 run scripts/load-test/smoke-test.js
//
// 目标:
//   P95 下单 < 1s
//   错误率 < 0.5%
// ============================================================

import http from 'k6/http';
import { check, sleep, group } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomIntBetween, randomItem } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ── 自定义指标 ──
const orderP95    = new Trend('order_duration_ms');
const paymentP95  = new Trend('payment_duration_ms');
const errorRate   = new Rate('error_rate');
const orderCount  = new Counter('order_count');

// ── 服务地址 ──
// BFF 聚合层（首页 feed / 商品详情）
const BFF_URL    = __ENV.BFF_URL    || 'http://localhost:3000';
// 各后端服务（BFF 尚未代理 cart/order/payment，压测时直连）
const CART_URL   = __ENV.CART_URL   || 'http://localhost:8084';
const ORDER_URL  = __ENV.ORDER_URL  || 'http://localhost:8085';
const PAY_URL    = __ENV.PAY_URL    || 'http://localhost:8087';
// W9 seed 商品 SPU ID 列表
const PRODUCT_IDS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];

// ── 阶梯加压配置 ──
export const options = {
  stages: [
    { target: 100, duration: '2m' },   // 预热 2min → 100 并发
    { target: 100, duration: '3m' },   // 保持 100 并发 3min
    { target: 500, duration: '2m' },   // 拉升到 500
    { target: 500, duration: '3m' },   // 保持 500
    { target: 1000, duration: '2m' },  // 拉升到 1000
    { target: 1000, duration: '3m' },  // 保持 1000
    { target: 2000, duration: '2m' },  // 拉升到 2000
    { target: 2000, duration: '3m' },  // 保持 2000
    { target: 0, duration: '1m' },     // 平稳下降
  ],
  thresholds: {
    http_req_duration: ['p(95)<1000'], // P95 < 1s
    error_rate:        ['rate<0.005'],  // 错误率 < 0.5%
  },
};

// ── 用户会话数据 ──
const users = [];
// 预生成 2000 个虚拟用户（压测前无需注册，BFF 支持 JWT 直接指定 userId）
for (let i = 0; i < 2000; i++) {
  users.push({
    userId: `loadtest_${String(i).padStart(4, '0')}`,
    token:  `loadtest_token_${i}`,  // 压测环境 BFF 应开放测试 token 验证
    phone:  `1380000${String(i).padStart(4, '0')}`,
  });
}

// ── 工具函数 ──

function getHeaders(user) {
  return {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${user.token}`,
  };
}

/** 安全请求：捕获 HTTP 错误，不抛异常 */
function safeGet(url, params, user) {
  const resp = http.get(url, { headers: getHeaders(user), ...params });
  check(resp, { 'status 200': (r) => r.status === 200 });
  if (resp.status >= 400) errorRate.add(1);
  return resp.status === 200 ? resp.json() : null;
}

function safePost(url, body, user) {
  const resp = http.post(url, JSON.stringify(body), { headers: getHeaders(user) });
  check(resp, { 'status 200': (r) => r.status === 200 });
  if (resp.status >= 400) errorRate.add(1);
  return resp.status === 200 ? resp.json() : null;
}

// ── 主场景 ──

export default function () {
  // 从虚拟用户池中取一个用户
  const vu = __VU - 1; // __VU 从 1 开始
  const user = users[vu % users.length];

  group('1 - 浏览商品', function () {
    // 首页 feed（走 BFF）
    const feed = safeGet(`${BFF_URL}/home/feed`, {}, user);
    if (!feed) return;

    // 随机看一个商品详情（走 BFF）
    const spuId = randomItem(PRODUCT_IDS);
    const detail = safeGet(`${BFF_URL}/product/${spuId}/detail`, {}, user);
    if (!detail) return;

    sleep(randomIntBetween(1, 3));
  });

  group('2 - 加购', function () {
    // 加购直连 cart-service（BFF 尚未代理该接口）
    const body = { spuId: randomItem(PRODUCT_IDS), count: randomIntBetween(1, 2) };
    const resp = safePost(`${CART_URL}/cart/add`, body, user);
    if (!resp) return;
    sleep(randomIntBetween(1, 2));
  });

  group('3 - 下单', function () {
    const start = Date.now();
    const body = {
      requestId: `loadtest_${user.userId}_${Date.now()}`,
      skuItems: [{ skuId: randomItem(PRODUCT_IDS), count: 1 }],
    };
    const resp = safePost(`${ORDER_URL}/order/create`, body, user);
    if (!resp) return;

    const duration = Date.now() - start;
    orderP95.add(duration);
    orderCount.add(1);

    const orderId = resp.data?.orderId;
    if (!orderId) return;

    sleep(randomIntBetween(1, 2));

    group('4 - 支付（沙箱）', function () {
      const payStart = Date.now();
      const payResp = safePost(`${PAY_URL}/payment/sandbox/mock-pay`, { orderId, payMethod: 'balance' }, user);
      if (payResp) paymentP95.add(Date.now() - payStart);
    });
  });

  sleep(randomIntBetween(2, 5));
}

// ── 自定义摘要输出 ──

export function handleSummary(data) {
  const metrics = {
    // 各阶段耗时
    p95_order:    data.metrics.order_duration_ms?.values?.p(95)?.toFixed(2) || 'N/A',
    p95_payment:  data.metrics.payment_duration_ms?.values?.p(95)?.toFixed(2) || 'N/A',
    avg_order:    data.metrics.order_duration_ms?.values?.avg?.toFixed(2) || 'N/A',
    avg_payment:  data.metrics.payment_duration_ms?.values?.avg?.toFixed(2) || 'N/A',
    // 整体
    total_orders: data.metrics.order_count?.values?.count || 0,
    error_pct:    (data.metrics.error_rate?.values?.rate || 0) * 100,
    // 请求
    total_reqs:   data.metrics.http_reqs?.values?.count || 0,
    rps:          data.metrics.http_req_rate?.values?.rate?.toFixed(2) || 'N/A',
    p95_all:      data.metrics.http_req_duration?.values?.p(95)?.toFixed(2) || 'N/A',
    p99_all:      data.metrics.http_req_duration?.values?.p(99)?.toFixed(2) || 'N/A',
  };

  const summary = `
========================================
  智购 · 全链路压测报告
========================================
  总请求数:    ${metrics.total_reqs}
  平均 RPS:    ${metrics.rps}
  总订单数:    ${metrics.total_orders}
  错误率:      ${metrics.error_pct.toFixed(2)}%

  --- 延迟 ---
  下单 P95:    ${metrics.p95_order} ms
  支付 P95:    ${metrics.p95_payment} ms
  下单平均:    ${metrics.avg_order} ms
  支付平均:    ${metrics.avg_payment} ms
  全局 P95:    ${metrics.p95_all} ms
  全局 P99:    ${metrics.p99_all} ms

  目标: P95 下单 < 1000ms  ✅ / ❌
  目标: 错误率 < 0.5%      ✅ / ❌
========================================
`;

  return {
    stdout: summary,
    [`${__ENV.REPORT_DIR || 'docs/load-test'}/report-${Date.now()}.json`]: JSON.stringify(metrics, null, 2),
  };
}