# ============================================================
# 智购 · 修复 RocketMQ Broker 注册地址（Windows Docker Desktop）
#
# 背景：broker 容器默认向 namesrv 注册「容器内网 IP」（如 172.18.0.7），
#       Docker Desktop 的 Windows 宿主机无法直连该地址，导致 Java 生产者
#       syncSend 一直 sendDefaultImpl call timeout。
# 修复：把 broker.conf（brokerIP1=127.0.0.1，经 10911 端口映射可达）覆盖到
#       broker 容器默认配置路径 $ROCKETMQ_HOME/conf/broker.conf，然后 restart。
# 用法：powershell -ExecutionPolicy Bypass -File scripts/mq-fix-broker-ip.ps1
# 验证：docker exec zhigou-rocketmq-namesrv sh -c "sh mqadmin clusterList -n localhost:9876"
#       期望 Addr 显示 127.0.0.1:10911
# ============================================================
$ErrorActionPreference = "Stop"

$conf = @"
brokerIP1 = 127.0.0.1
"@
$tmp = Join-Path $env:TEMP "zhigou-broker.conf"
[IO.File]::WriteAllText($tmp, $conf, [Text.UTF8Encoding]::new($false))

Write-Host "[1/3] 写入 broker.conf -> $tmp"
docker cp $tmp "zhigou-rocketmq-broker:/home/rocketmq/rocketmq-5.3.0/conf/broker.conf"
if ($LASTEXITCODE -ne 0) { throw "docker cp 失败" }

Write-Host "[2/3] 重启 broker 容器（保留可写层，不 recreate）"
docker restart zhigou-rocketmq-broker
Start-Sleep -Seconds 15

Write-Host "[3/3] 复核注册地址（期望 127.0.0.1:10911）"
docker exec zhigou-rocketmq-namesrv sh -c "sh mqadmin clusterList -n localhost:9876" 2>&1 | Select-Object -First 4
Remove-Item $tmp -ErrorAction SilentlyContinue
