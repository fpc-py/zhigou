$root = "D:\aafpc\Java\demo\zhigou\zhigou"
$services = @(
  'auth-center','product-service','cart-service','order-service',
  'inventory-service','payment-service','marketing-service',
  'logistics-service','aftersale-service','user-service','file-service'
)

foreach ($svc in $services) {
  Write-Host "Starting $svc ..." -ForegroundColor Cyan
  Start-Process -FilePath "cmd.exe" `
    -ArgumentList "/k","mvn spring-boot:run -pl services/$svc" `
    -WorkingDirectory $root
  Start-Sleep -Seconds 2   # 错开启动，避免同时抢 CPU/内存
}