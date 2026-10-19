// circuit-breaker 状态机确定性测试
import { CircuitBreaker } from './src/common/circuit-breaker.ts';

function mk(over = {}) {
  return new CircuitBreaker({ windowMs: 10000, failureThreshold: 0.5, minCalls: 5, openMs: 60, ...over });
}

const results = [];
function check(name, cond) {
  results.push([name, cond]);
  console.log((cond ? 'PASS' : 'FAIL') + ' ' + name);
}

(async () => {
  // 1. CLOSED：minCalls-1 次失败不触发
  const b1 = mk();
  for (let i = 0; i < 4; i++) { b1.recordFailure(); }
  check('1. 4次失败未触发(minCalls=5)', b1.allow() === true && b1.getState() === 'CLOSED');

  // 2. 第5次失败 → OPEN，快速失败
  b1.recordFailure();
  check('2. 5次失败→OPEN', b1.getState() === 'OPEN');
  check('2b. OPEN 拒绝调用', b1.allow() === false);

  // 3. openMs 到期 → HALF_OPEN，放行1个探活
  await new Promise((r) => setTimeout(r, 90));
  check('3. 到期→HALF_OPEN 放行探活', b1.allow() === true && b1.getState() === 'HALF_OPEN');
  check('3b. 半开期间其余拒绝', b1.allow() === false);

  // 4. 半开成功 → CLOSED
  b1.recordSuccess();
  check('4. 半开成功→CLOSED', b1.getState() === 'CLOSED' && b1.allow() === true);

  // 5. 半开失败 → 重新 OPEN
  const b2 = mk({ openMs: 40 });
  for (let i = 0; i < 5; i++) b2.recordFailure();
  await new Promise((r) => setTimeout(r, 60));
  b2.allow(); // 进入半开并放行探活
  b2.recordFailure();
  check('5. 半开失败→重新OPEN', b2.getState() === 'OPEN' && b2.allow() === false);

  // 6. 窗口重置：窗口外失败不计
  const b3 = mk({ windowMs: 30, minCalls: 5 });
  b3.recordFailure(); await new Promise((r) => setTimeout(r, 50)); b3.recordFailure();
  check('6. 窗口滚动后计数重置', b3.allow() === true);

  const failed = results.filter(([, c]) => !c);
  console.log('RESULT', failed.length === 0 ? 'ALL_PASS' : 'HAS_FAIL');
  process.exit(failed.length === 0 ? 0 : 1);
})();
