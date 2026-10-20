# 故障演练脚本

探针式故障演练（无第三方依赖），配合 `docs/故障演练与Runbook.md` 使用。

```powershell
# 场景1：下游宕机 → 熔断降级（先停掉目标服务，如 order 8085）
python run_drill.py --scenario circuit_breaker --port 8085

# 场景2：缓存击穿 → Caffeine 单飞（需 product-service 8083 运行）
python run_drill.py --scenario cache_storm --spu 1 --concurrency 100

# 场景3：MQ 积压 → 探针指标（观测指引）
python run_drill.py --scenario mq_backlog
```

每次演练后按 Runbook 复盘模板填写结果。
