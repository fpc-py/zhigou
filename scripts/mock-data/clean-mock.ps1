#Requires -Version 5.1
<#
.SYNOPSIS
    智购 · Mock 数据清除脚本（上线前使用）

.DESCRIPTION
    快速清除各服务数据库中的 mock / 测试数据，保留表结构（TRUNCATE）。
    同时清除 Redis 中的购物车 key（cart:*）。
    说明：清除范围包含 DDL 迁移里自带的种子数据（category / brand / coupon_template 等），
          上线时应一并清除，由运营重新录入正式基础数据。

.PARAMETER MysqlContainer
    MySQL 容器名，默认 zhigou-mysql

.PARAMETER RedisContainer
    Redis 容器名，默认 zhigou-redis

.PARAMETER SkipConfirm
    跳过交互确认（CI / 自动化场景使用）

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1

.NOTES
    仅在确认要清空测试数据时执行。生产环境执行前请务必备份数据库！
#>
param(
    [string]$MysqlContainer = "zhigou-mysql",
    [string]$MysqlUser      = "root",
    [string]$MysqlPassword  = "123456",
    [string]$RedisContainer = "zhigou-redis",
    [switch]$SkipConfirm
)

$ErrorActionPreference = "Stop"

# ── 库 → 待清空的表 ──
$Databases = [ordered]@{
    "zhigou_auth"      = @("user")
    "zhigou_user"      = @("user", "address")
    "zhigou_file"      = @("file_meta")
    "zhigou_product"   = @("product_spu", "product_sku", "category", "brand")
    "zhigou_order"     = @("order_main", "order_item", "outbox")
    "zhigou_inventory" = @("stock")
    "zhigou_payment"   = @("payment")
    "zhigou_marketing" = @("user_coupon", "discount_snapshot", "coupon_template", "promotion_rule")
    "zhigou_logistics" = @("track_event", "shipment", "freight_template")
    "zhigou_aftersale" = @("aftersale_order")
}

Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host " 智购 · Mock 数据清除（TRUNCATE，保留表结构）" -ForegroundColor Yellow
Write-Host "==========================================================" -ForegroundColor Yellow
Write-Host ""
Write-Host "将清空以下库表：" -ForegroundColor Cyan
foreach ($db in $Databases.Keys) {
    Write-Host ("  {0,-20} {1}" -f $db, ($Databases[$db] -join ", "))
}
Write-Host "  redis(cart:*)        购物车 key" -ForegroundColor Cyan
Write-Host ""

if (-not $SkipConfirm) {
    $answer = Read-Host "确认执行清除？此操作不可恢复 (y/N)"
    if ($answer -notin @("y", "Y", "yes", "YES")) {
        Write-Host "已取消。" -ForegroundColor Yellow
        exit 0
    }
}

# ── 检查 MySQL 容器是否运行 ──
$mysqlRunning = docker ps --filter "name=^/$MysqlContainer$" --format "{{.Names}}" 2>$null
if ($mysqlRunning -ne $MysqlContainer) {
    Write-Host "错误：未找到运行中的 MySQL 容器 '$MysqlContainer'。" -ForegroundColor Red
    exit 1
}

# ── 逐库执行 TRUNCATE ──
foreach ($db in $Databases.Keys) {
    $tables = $Databases[$db]
    $sql = "SET FOREIGN_KEY_CHECKS=0;"
    foreach ($t in $tables) {
        $sql += " TRUNCATE TABLE ``$t``;"
    }
    $sql += " SET FOREIGN_KEY_CHECKS=1;"

    Write-Host "Cleaning $db ..." -NoNewline
    docker exec $MysqlContainer mysql -u$MysqlUser -p$MysqlPassword -D $db -e $sql 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " FAILED (exit=$LASTEXITCODE)" -ForegroundColor Red
    }
}

# ── 清除 Redis 购物车 key ──
Write-Host "Cleaning redis cart:* ..." -NoNewline
$redisRunning = docker ps --filter "name=^/$RedisContainer$" --format "{{.Names}}" 2>$null
if ($redisRunning -eq $RedisContainer) {
    $keys = docker exec $RedisContainer redis-cli --scan --pattern "cart:*" 2>$null
    if ($keys) {
        foreach ($k in $keys) {
            docker exec $RedisContainer redis-cli DEL $k 2>$null | Out-Null
        }
        Write-Host " OK ($(@($keys).Count) keys)" -ForegroundColor Green
    } else {
        Write-Host " OK (no keys)" -ForegroundColor Green
    }
} else {
    Write-Host " SKIP (redis container not running)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Mock 数据清除完成。" -ForegroundColor Green
Write-Host "提示：如已激活过 dev profile，请重新启动服务时不要带 --spring.profiles.active=dev。" -ForegroundColor Yellow
