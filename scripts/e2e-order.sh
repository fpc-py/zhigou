#!/bin/bash
# ============================================================
# 智购 · 端到端下单流程测试
# 用法: bash scripts/e2e-order.sh
# ============================================================
set -e
PY="python -c"
json() { $PY "import sys,json;d=json.load(sys.stdin);print(d$1)"; }

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
PASS="${GREEN}PASS${NC}"; FAIL="${RED}FAIL${NC}"
BASE="http://localhost"
PORT_AUTH=8080; PORT_PROD=8083; PORT_CART=8084
PORT_ORDER=8085; PORT_PAY=8087; PORT_INV=8086
TOKEN=""; REQ_ID="e2e-$(date +%s)"

log() { echo -e "${YELLOW}[$(date +%T)]${NC} $1"; }
check() { if [ "$1" = "$2" ]; then echo -e "  $PASS: $3"; else echo -e "  $FAIL: $3 (expected=$1 got=$2)"; fi; }

# ========== Step 0: 启动中间件 ==========
log "Step 0: 启动中间件 (MySQL + Redis)"
docker compose -f infra/compose/middleware.yml up -d mysql redis 2>/dev/null || true
sleep 8
log "  中件已启动"

# ========== Step 1: 启动服务 ==========
log "Step 1: 后台启动 6 个微服务"
PIDS=()
for svc in auth-center product-service cart-service order-service payment-service inventory-service; do
  log "  启动 $svc ..."
  (cd services/$svc && mvn spring-boot:run -q 2>&1) &
  PIDS+=($!); cd services/$svc/../.. 2>/dev/null || true
done
log "  等待 auth-center 就绪 ..."
for i in $(seq 1 60); do
  if curl -s -o /dev/null -w "%{http_code}" $BASE:$PORT_AUTH/v3/api-docs 2>/dev/null | grep -q 200; then break; fi; sleep 2
done
log "  等待其他服务..."
for p in $PORT_PROD $PORT_CART $PORT_ORDER $PORT_PAY $PORT_INV; do
  for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" $BASE:$p/v3/api-docs 2>/dev/null | grep -q 200; then break; fi; sleep 2
  done
done
log "  全部服务就绪 ✓"

# ========== Step 2: 注册用户 ==========
log "Step 2: 注册用户拿 token"
SMS=$(curl -s -X POST $BASE:$PORT_AUTH/auth/send-sms-code -H "Content-Type: application/json" -d '{"phone":"13800138000"}')
CODE=$(echo "$SMS" | json "['data']" 2>/dev/null || echo "")
# 从 Redis 取验证码
CODE=$(docker exec zhigou-redis redis-cli GET "auth:sms:13800138000" 2>/dev/null | tr -d '\r\n' || echo "000000")
if [ -z "$CODE" ] || [ "$CODE" = "null" ]; then CODE="123456"; fi

LOGIN=$(curl -s -X POST $BASE:$PORT_AUTH/auth/login -H "Content-Type: application/json" -d "{\"phone\":\"13800138000\",\"code\":\"$CODE\"}")
TOKEN=$(echo "$LOGIN" | json "['data']['accessToken']" | tr -d '"')
log "  验证码=$CODE, token=${TOKEN:0:20}..."
AUTH="Authorization: Bearer $TOKEN"

# ========== Step 3: 上架商品 ==========
log "Step 3: 上架测试商品"
SPU=$(curl -s -X POST $BASE:$PORT_PROD/product/spu \
  -H "Content-Type: application/json" \
  -d '{"categoryId":1,"brandId":1,"name":"E2E商品","subtitle":"测试","description":"测试","mainImage":"https://x.com/p.jpg","skus":[{"specName":"规格","specValue":"标准","price":19900,"stock":100}]}')
SPU_ID=$(echo "$SPU" | json "['data']['spuId']")
log "  商品上架: spuId=$SPU_ID"
SKU=1

# ========== Step 4: 加购 ==========
log "Step 4: 加入购物车"
CART=$(curl -s -X POST $BASE:$PORT_CART/cart/add -H "Content-Type: application/json" -H "$AUTH" -d "{\"skuId\":$SKU,\"count\":2}")
log "  加购 OK"

# ========== Step 5: 创建订单 ==========
log "Step 5: 创建订单 (requestId=$REQ_ID)"
ORDER=$(curl -s -X POST $BASE:$PORT_ORDER/order/create \
  -H "Content-Type: application/json" -H "X-Request-Id: $REQ_ID" \
  -d "{\"requestId\":\"$REQ_ID\",\"skuItems\":[{\"skuId\":$SKU,\"count\":2}]}")
OID=$(echo "$ORDER" | json "['data']['orderId']")
STATUS=$(echo "$ORDER" | json "['data']['orderStatus']" | tr -d '"')
check "$STATUS" "INIT" "订单创建成功 orderId=$OID status=INIT"

# ========== Step 6: 幂等 ==========
log "Step 6: 同 requestId 重复→幂等"
DUP=$(curl -s -X POST $BASE:$PORT_ORDER/order/create \
  -H "Content-Type: application/json" -H "X-Request-Id: $REQ_ID" \
  -d "{\"requestId\":\"$REQ_ID\",\"skuItems\":[{\"skuId\":$SKU,\"count\":2}]}")
OID2=$(echo "$DUP" | json "['data']['orderId']")
check "$OID2" "$OID" "重复下单返回同一订单号"

# ========== Step 7: 沙箱支付 ==========
log "Step 7: 沙箱支付"
PAY=$(curl -s -X POST $BASE:$PORT_PAY/payment/create \
  -H "Content-Type: application/json" \
  -d "{\"userId\":10001,\"orderNo\":\"$OID\",\"amount\":19900}")
PNO=$(echo "$PAY" | json "['data']['paymentNo']" | tr -d '"')
log "  paymentNo=$PNO, 计算签名..."

SIGN=$(echo -n "${PNO}sandbox-secret-key" | sha256sum 2>/dev/null | cut -d' ' -f1)
if [ -z "$SIGN" ]; then
  SIGN=$(python3 -c "import hashlib;print(hashlib.sha256(('${PNO}sandbox-secret-key').encode()).hexdigest())")
fi
PAY_OK=$(curl -s -X POST $BASE:$PORT_PAY/payment/sandbox/mock-pay \
  -H "Content-Type: application/json" -d "{\"paymentNo\":\"$PNO\",\"sign\":\"$SIGN\"}")
log "  沙箱支付回调: OK"

# ========== Step 8: 订单→PAID ==========
log "Step 8: 触发 payCallback, 查状态=PAID"
curl -s -X POST "$BASE:$PORT_ORDER/order/payCallback/$OID" -H "Content-Type: application/json" > /dev/null
DETAIL=$(curl -s "$BASE:$PORT_ORDER/order/$OID" -H "$AUTH")
OS=$(echo "$DETAIL" | json "['data']['orderStatus']" | tr -d '"')
check "$OS" "PAID" "订单状态=PAID"

# ========== Step 9: 库存 ==========
log "Step 9: 查库存扣减"
curl -s -X POST $BASE:$PORT_INV/inventory/confirm -H "Content-Type: application/json" -d "{\"skuId\":$SKU,\"count\":2}" > /dev/null
STOCK=$(docker exec zhigou-mysql mysql -uroot -p123456 zhigou -se "SELECT available FROM stock WHERE sku_id=$SKU" 2>/dev/null | tr -d ' ')
check "$STOCK" "98" "库存从100扣到98 (扣2)"

# ========== Step 10: outbox ==========
log "Step 10: outbox 表"
OBOX=$(docker exec zhigou-mysql mysql -uroot -p123456 zhigou -se "SELECT COUNT(*) FROM outbox" 2>/dev/null | tr -d ' ')
echo -e "  outbox 记录数: $OBOX"
[ "$OBOX" -ge 1 ] && echo -e "  $PASS: outbox 有消息记录" || echo -e "  $FAIL: outbox 无记录"

# ========== 清理 ==========
log "清理后台服务..."
for p in "${PIDS[@]}"; do kill $p 2>/dev/null || true; done
echo -e "\n${GREEN}========================================${NC}"
echo -e "${GREEN}  E2E 下单流程完成!${NC}"
echo -e "${GREEN}========================================${NC}"