# Zhigou Full-Chain Smoke Test (ASCII-safe for Windows PowerShell 5.1)
$ErrorActionPreference = 'Stop'
$BFF = 'http://localhost:3000'
$ORD = 'http://localhost:8085'
$INV = 'http://localhost:8086'
$LOG = 'http://localhost:8089'
$AFT = 'http://localhost:8090'
$PHONE = '13800138001'
$SKU_ID = '9000000000000000020'
$SPU_ID = '9000000000000000010'
$USER_ID = '9000000000000000001'

$script:pass = 0; $script:fail = 0
function Step($name, [scriptblock]$sb) {
  try {
    & $sb
    Write-Output ("[PASS] " + $name)
    $script:pass++
  } catch {
    Write-Output ("[FAIL] " + $name + " -> " + $_.Exception.Message)
    $script:fail++
  }
}
function J($obj) { return $obj | ConvertTo-Json -Depth 6 -Compress }

Write-Output ("===== Zhigou E2E Smoke " + (Get-Date -Format 'HH:mm:ss') + " =====")

$token = ''; $userId = ''
Step '1 send-sms-code' {
  $null = Invoke-RestMethod -Method Post -Uri "$BFF/auth/send-sms-code" -ContentType 'application/json' -Body (J @{phone=$PHONE})
}
Step '2 login' {
  $code = (docker exec zhigou-redis redis-cli GET "auth:sms:$PHONE") -replace "`r|`n",""
  if ([string]::IsNullOrWhiteSpace($code)) { $code = '123456' }
  $r = Invoke-RestMethod -Method Post -Uri "$BFF/auth/login" -ContentType 'application/json' -Body (J @{phone=$PHONE; code=$code})
  $script:token = $r.data.accessToken
  $script:userId = $r.data.userId
  if (-not $script:token) { throw 'no accessToken' }
  Write-Output ("    token=" + $script:token.Substring(0,30) + "... userId=" + $script:userId)
}
$H = @{ Authorization = "Bearer $script:token"; 'Content-Type' = 'application/json' }

Step '3 product list' {
  $r = Invoke-RestMethod -Method Get -Uri "$BFF/product/page?pageNum=1&pageSize=5" -Headers $H
  $n = @($r.data.records).Count
  if ($n -eq 0) { throw 'empty product list' }
  Write-Output ("    spus=" + $n + " first=" + $r.data.records[0].name)
}
Step '4 product detail' {
  $r = Invoke-RestMethod -Method Get -Uri "$BFF/product/$SPU_ID/detail" -Headers $H
  # BFF 聚合结构：{ product: SpuDetailResponse, aiReason: ... }
  if ([string]$r.data.product.spuId -ne $SPU_ID) { throw 'spu mismatch' }
  Write-Output ("    " + $r.data.product.name + " skus=" + @($r.data.product.skus).Count)
}

Step '5 AI chat SSE' {
  $body = J @{ query='recommend lightweight running shoes'; sessionId=("smoke-" + (Get-Date -Format 'yyyyMMddHHmmss')) }
  $r = Invoke-WebRequest -Method Post -Uri "$BFF/chat/sse" -Headers $H -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 90
  $txt = $r.Content
  if ([string]::IsNullOrWhiteSpace($txt)) { throw 'empty SSE response' }
  Write-Output ("    SSE len=" + $txt.Length + " head=" + $txt.Substring(0,[Math]::Min(100,$txt.Length)).Replace("`n"," "))
}

Step '6 cart add' {
  $null = Invoke-RestMethod -Method Post -Uri "$BFF/cart/add" -Headers $H -ContentType 'application/json' -Body (J @{skuId=$SKU_ID; count=2})
}
Step '7 cart mine' {
  $r = Invoke-RestMethod -Method Get -Uri "$BFF/cart/mine" -Headers $H
  # BFF 返回 data 为数组（购物车条目），非 {items: [...]}
  $items = @($r.data)
  if ($items.Count -eq 0) { throw 'empty cart' }
  Write-Output ("    items=" + $items.Count + " sku=" + $items[0].skuId + " x" + $items[0].count)
}

$script:orderId = ''
Step '8 order create' {
  $reqId = "smoke-" + (Get-Date -Format 'yyyyMMddHHmmssfff')
  $body = J @{ requestId=$reqId; skuItems=@(@{skuId=$SKU_ID; count=1}) }
  $r = Invoke-RestMethod -Method Post -Uri "$BFF/order/create" -Headers $H -ContentType 'application/json' -Body $body
  $script:orderId = [string]$r.data.orderId
  if (-not $script:orderId) { throw 'no orderId' }
  Write-Output ("    orderId=" + $script:orderId + " status=" + $r.data.orderStatus + " pay=" + $r.data.payAmount)
}
Step '9 idempotency' {
  # 幂等：同一 requestId 发两次，两次返回同一 orderId
  $reqId2 = "smoke-dup" + (Get-Date -Format 'yyyyMMddHHmmssfff')
  $body = J @{ requestId=$reqId2; skuItems=@(@{skuId=$SKU_ID; count=1}) }
  $r1 = Invoke-RestMethod -Method Post -Uri "$BFF/order/create" -Headers $H -ContentType 'application/json' -Body $body
  $r2 = Invoke-RestMethod -Method Post -Uri "$BFF/order/create" -Headers $H -ContentType 'application/json' -Body $body
  $oid1 = [string]$r1.data.orderId; $oid2 = [string]$r2.data.orderId
  if ($oid2 -ne $oid1) { throw "idempotency fail: $oid1 vs $oid2" }
  Write-Output ("    dup requestId -> same order " + $oid2)
}

$script:paymentNo = ''
Step '10 payment create' {
  $r = Invoke-RestMethod -Method Post -Uri "$BFF/payment/create" -Headers $H -ContentType 'application/json' -Body (J @{orderNo=$script:orderId; amount=9900})
  $script:paymentNo = [string]$r.data.paymentNo
  if (-not $script:paymentNo) { throw 'no paymentNo' }
  Write-Output ("    paymentNo=" + $script:paymentNo)
}
Step '11 sandbox mock-pay' {
  $sha = [Security.Cryptography.SHA256]::Create()
  $hash = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($script:paymentNo + "sandbox-secret-key"))
  $sign = ($hash | ForEach-Object { $_.ToString('x2') }) -join ''
  $null = Invoke-RestMethod -Method Post -Uri "$BFF/payment/sandbox/mock-pay" -Headers $H -ContentType 'application/json' -Body (J @{paymentNo=$script:paymentNo; sign=$sign})
}
Step '12 order payCallback' {
  $null = Invoke-RestMethod -Method Post -Uri "$ORD/order/payCallback/$($script:orderId)"
  $r = Invoke-RestMethod -Method Get -Uri "$BFF/order/$($script:orderId)" -Headers $H
  if ($r.data.orderStatus -ne 'PAID') { throw "status=" + $r.data.orderStatus + " expect PAID" }
  Write-Output ("    order status=" + $r.data.orderStatus)
}

Step '13 inventory confirm' {
  # direct call needs internal passthrough header (JwtAuthFilter auth)
  $null = Invoke-RestMethod -Method Post -Uri "$INV/inventory/confirm" -Headers @{ 'x-user-id'=$script:userId } -ContentType 'application/json' -Body (J @{skuId=[long]$SKU_ID; count=1})
}
Step '14 shipment create' {
  $r = Invoke-RestMethod -Method Post -Uri "$LOG/shipment/create" -Headers @{ 'x-user-id'=$script:userId } -ContentType 'application/json' -Body (J @{orderNo=$script:orderId; userId=[long]$script:userId; receiverAddr="{}"})
  Write-Output ("    shipmentNo=" + $r.data)
}
$script:afterNo = ''
Step '15 aftersale apply' {
  $body = J @{orderNo=$script:orderId; type='refund'; reason='e2e smoke'; amount=9900}
  $r = Invoke-RestMethod -Method Post -Uri "$AFT/aftersale/apply" -Headers @{ 'x-user-id'=$script:userId } -ContentType 'application/json' -Body $body
  $script:afterNo = [string]$r.data.aftersaleNo
  if (-not $script:afterNo) { throw 'no aftersaleNo' }
  Write-Output ("    aftersaleNo=" + $script:afterNo)
}
Step '16 aftersale approve' {
  $null = Invoke-RestMethod -Method Post -Uri "$AFT/aftersale/$($script:afterNo)/approve" -Headers @{ 'x-user-id'=$script:userId }
}

Step '17 order mine' {
  $r = Invoke-RestMethod -Method Get -Uri "$ORD/order/mine?userId=$script:userId"
  $ids = @($r.data | ForEach-Object { [string]$_.orderId })
  if ($ids -notcontains $script:orderId) { throw "order $script:orderId not in mine" }
  Write-Output ("    orders=" + $ids.Count)
}

Write-Output ""
Write-Output ("===== RESULT: PASS=" + $script:pass + " FAIL=" + $script:fail + " =====")
exit $(if ($script:fail -gt 0) { 1 } else { 0 })
