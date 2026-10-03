# 智购 · Mock 数据 Flyway Profile 分离方案

## 目标
- **测试/演示**：启动时自动导入 mock 数据（11 个服务全覆盖）
- **上线**：快速切换为干净状态（不导入 mock，已存在的 mock 行一键清除）
- **不破坏现有 DDL 迁移**：现有 V20261001~V20261008 已执行过，不动其内容，只在 dev 目录新增独立 mock 迁移

## 当前状态分析

### 现有 mock 数据散落情况
| 服务 | 迁移文件 | 是否含 INSERT | mock 内容 |
|---|---|---|---|
| product-service | V20261001__product_init.sql | 是 | category(4 行) + brand(1 行) |
| marketing-service | V20261007__marketing_init.sql | 是 | coupon_template(3 行) + promotion_rule(1 行) |
| logistics-service | V20261008__logistics_init.sql | 是 | freight_template(1 行) |
| inventory-service | V20261001__stock_init.sql | 是 | stock(3 行) |
| 其他 7 服务 | 各 V*.sql | 否 | 仅 DDL |

### 现有 Flyway 配置
- 10 个服务主 `application.yml` 已配 `baseline-on-migrate: true` + `validate-on-migrate: false`
- 部分 service 配置了 `table: flyway_<svc>_history`
- user-service 已有 `application-dev.yml`（仅日志级别）
- 所有服务用 `spring.flyway.locations: classpath:db/migration`

## 提议变更

### 变更 1：每个服务新增 `db/migration/dev/` 目录与 mock SQL

为 11 个服务各新增 `src/main/resources/db/migration/dev/V20261090__mock_<svc>.sql`：
- 版本号统一用 `20261090`（高于现有所有 V20261001~V20261008，确保在 DDL 之后执行）
- 文件清单：
  - `services/auth-center/src/main/resources/db/migration/dev/V20261090__mock_auth.sql`：插入测试用户（13800138000 等 3 个手机号）
  - `services/user-service/src/main/resources/db/migration/dev/V20261090__mock_user.sql`：用户档案 + 收货地址
  - `services/file-service/src/main/resources/db/migration/dev/V20261090__mock_file.sql`：文件元数据
  - `services/product-service/src/main/resources/db/migration/dev/V20261090__mock_product.sql`：补齐 SPU/SKU（保留现有 DDL 里的 category/brand，新增 3 个 SPU + 6 个 SKU）
  - `services/cart-service/src/main/resources/db/migration/dev/V20261090__mock_cart.sql`：占位空文件（cart 用 Redis，无 SQL；但保留目录结构，便于后续扩展）
  - `services/order-service/src/main/resources/db/migration/dev/V20261090__mock_order.sql`：示例订单 + 订单项
  - `services/inventory-service/src/main/resources/db/migration/dev/V20261090__mock_inventory.sql`：补齐 SKU 库存（与 product mock 的 skuId 对齐）
  - `services/payment-service/src/main/resources/db/migration/dev/V20261090__mock_payment.sql`：示例支付记录
  - `services/marketing-service/src/main/resources/db/migration/dev/V20261090__mock_marketing.sql`：补齐 user_coupon（保留现有 DDL 里的 coupon_template/promotion_rule）
  - `services/logistics-service/src/main/resources/db/migration/dev/V20261090__mock_logistics.sql`：示例运单 + 轨迹（保留现有 DDL 里的 freight_template）
  - `services/aftersale-service/src/main/resources/db/migration/dev/V20261090__mock_aftersale.sql`：示例售后单

**为什么不修改现有 V20261001~V20261008？**
Flyway 已记录这些版本为 success=1，修改内容会触发 checksum 校验失败（即使 validate-on-migrate=false，也建议保持原文件不变以避免历史不一致）。新 mock 数据用更高版本号 V20261090 独立迁移，逻辑清晰。

**ID 对齐约定**（避免外键冲突）：
- mock 用户 user_id = `9000000000000000001` ~ `9000000000000000003`（避开 Snowflake 真实 ID 段）
- mock SPU spu_id = `9000000000000000010` ~ `9000000000000000012`
- mock SKU sku_id = `9000000000000000020` ~ `9000000000000000025`
- mock 订单 order_id = `9000000000000000100` ~ `9000000000000000102`
- mock 售后单 aftersale_no = `MOCK_AS_001` ~ `MOCK_AS_003`

### 变更 2：每个服务的 `application-dev.yml` 配置 Flyway dev locations

为 11 个服务创建/更新 `src/main/resources/application-dev.yml`：

```yaml
spring:
  config:
    activate:
      on-profile: dev
  flyway:
    locations: classpath:db/migration,classpath:db/migration/dev
```

主 `application.yml` 保持 `locations: classpath:db/migration`（仅 DDL）。

**切换方式**：
- 测试/演示：`java -jar xxx.jar --spring.profiles.active=dev`
- 上线：`java -jar xxx.jar`（不激活 dev，mock 不导入）

### 变更 3：新增一键清除脚本 `scripts/mock-data/clean-mock.ps1`

用 PowerShell + docker exec mysql 批量 TRUNCATE 各库 mock 表（保留表结构）：

```powershell
# scripts/mock-data/clean-mock.ps1
# 用法: powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1

$mysqlContainer = "zhigou-mysql"
$mysqlPassword = "123456"

# 库 → 待 TRUNCATE 表
$databases = @{
  "zhigou_auth"      = @("user")
  "zhigou_user"      = @("user", "address")
  "zhigou_file"      = @("file_meta")
  "zhigou_product"   = @("product_spu", "product_sku", "category", "brand")
  "zhigou_order"     = @("order_item", "order_main", "outbox")
  "zhigou_inventory" = @("stock")
  "zhigou_payment"   = @("payment")
  "zhigou_marketing" = @("user_coupon", "discount_snapshot", "coupon_template", "promotion_rule")
  "zhigou_logistics" = @("track_event", "shipment", "freight_template")
  "zhigou_aftersale" = @("aftersale_order")
}

foreach ($db in $databases.Keys) {
  $tables = $databases[$db] -join ", "
  Write-Host "Cleaning $db: $tables"
  docker exec $mysqlContainer mysql -uroot -p$mysqlPassword -D $db -e "SET FOREIGN_KEY_CHECKS=0; TRUNCATE TABLE $($tables -replace ',', '; TRUNCATE TABLE '); SET FOREIGN_KEY_CHECKS=1;" 2>$null
}
# 同步清 Redis 购物车
docker exec zhigou-redis redis-cli --scan --pattern "cart:*" | docker exec -i zhigou-redis redis-cli -x DEL
Write-Host "Mock data cleaned."
```

**清除范围说明**：
- 包含 DDL 迁移里自带的种子数据（category/brand/coupon_template 等），因为上线时这些也应清除
- 用 `SET FOREIGN_KEY_CHECKS=0` 避免外键约束阻塞
- 同步清除 Redis 中的购物车 key

### 变更 4：新增上线检查清单 `docs/mock-data-cleanup-guide.md`

包含：
- 上线前清除 mock 数据的完整步骤
- 验证清除结果的 SQL
- Flyway dev profile 不激活的启动命令
- 回滚预案

## 假设与决策

1. **不动现有 DDL 迁移文件**：V20261001~V20261008 保持原样（含 INSERT 也保留），新 mock 用 V20261090 独立迁移。理由：避免 checksum 不一致；现有 INSERT 的种子数据（category/brand/coupon_template）在 prod 环境也用得上（基础数据），不算严格意义上的 mock。
2. **mock 数据用固定 ID 段 `9000...`**：避开 Snowflake 真实 ID，便于识别和清除。
3. **清除策略用 TRUNCATE 而非 DELETE**：TRUNCATE 重置自增 ID，更彻底；保留表结构。
4. **cart-service 的 mock 目录保留空 SQL 文件**：保持 11 服务目录结构一致，便于后续扩展。
5. **dev profile 切换通过启动参数 `--spring.profiles.active=dev`**：不修改主 application.yml，prod 启动时不带该参数即可。
6. **不使用 `spring.flyway.clean-on-migrate`**：clean 会删除整个 schema，过于危险；用 TRUNCATE 脚本更可控。

## 验证步骤

1. **验证 dev profile 导入 mock**：
   ```powershell
   # 启动任意服务带 dev profile
   $env:SPRING_PROFILES_ACTIVE = "dev"
   java -jar services/product-service/target/zhigou-product-service-0.1.0-SNAPSHOT.jar
   ```
   - 检查 `flyway_product_history` 表有 V20261090 记录 success=1
   - 查 `product_spu` 表有 mock SPU（spu_id 以 9000 开头）

2. **验证 prod profile 不导入 mock**：
   ```powershell
   # 不带 dev profile 启动
   java -jar services/product-service/target/zhigou-product-service-0.1.0-SNAPSHOT.jar
   ```
   - 检查 `flyway_product_history` 表没有 V20261090 记录
   - 查 `product_spu` 表无 mock SPU

3. **验证清除脚本**：
   ```powershell
   # 执行清除
   powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1
   # 验证表为空
   docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_product -e "SELECT COUNT(*) FROM product_spu;"
   ```

4. **验证完整 e2e 流程**：
   - 用 dev profile 启动所有服务
   - 跑 `scripts/e2e-order.sh`
   - 验证 mock 数据 + 流程产生的数据共存

## 文件清单（待创建/修改）

### 新增文件
- `services/auth-center/src/main/resources/db/migration/dev/V20261090__mock_auth.sql`
- `services/user-service/src/main/resources/db/migration/dev/V20261090__mock_user.sql`
- `services/file-service/src/main/resources/db/migration/dev/V20261090__mock_file.sql`
- `services/product-service/src/main/resources/db/migration/dev/V20261090__mock_product.sql`
- `services/cart-service/src/main/resources/db/migration/dev/V20261090__mock_cart.sql`（占位）
- `services/order-service/src/main/resources/db/migration/dev/V20261090__mock_order.sql`
- `services/inventory-service/src/main/resources/db/migration/dev/V20261090__mock_inventory.sql`
- `services/payment-service/src/main/resources/db/migration/dev/V20261090__mock_payment.sql`
- `services/marketing-service/src/main/resources/db/migration/dev/V20261090__mock_marketing.sql`
- `services/logistics-service/src/main/resources/db/migration/dev/V20261090__mock_logistics.sql`
- `services/aftersale-service/src/main/resources/db/migration/dev/V20261090__mock_aftersale.sql`
- `scripts/mock-data/clean-mock.ps1`
- `docs/mock-data-cleanup-guide.md`

### 修改文件
- `services/auth-center/src/main/resources/application-dev.yml`（新建）
- `services/user-service/src/main/resources/application-dev.yml`（已存在，追加 flyway 配置）
- `services/file-service/src/main/resources/application-dev.yml`（新建）
- `services/product-service/src/main/resources/application-dev.yml`（新建）
- `services/cart-service/src/main/resources/application-dev.yml`（新建）
- `services/order-service/src/main/resources/application-dev.yml`（新建）
- `services/inventory-service/src/main/resources/application-dev.yml`（新建）
- `services/payment-service/src/main/resources/application-dev.yml`（新建）
- `services/marketing-service/src/main/resources/application-dev.yml`（新建）
- `services/logistics-service/src/main/resources/application-dev.yml`（新建）
- `services/aftersale-service/src/main/resources/application-dev.yml`（新建）

## 上线检查清单（docs/mock-data-cleanup-guide.md 核心内容）

### 上线前 7 步
1. 备份生产数据库（`mysqldump` 全量备份）
2. 确认启动命令不带 `--spring.profiles.active=dev`
3. 执行 `powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1`
4. 验证清除：`SELECT COUNT(*) FROM product_spu WHERE spu_id LIKE '9%';` 应为 0
5. 启动所有服务（不带 dev profile）
6. 验证 `flyway_*_history` 表无 V20261090 记录
7. 跑 e2e 冒烟测试确认功能正常

### 回滚预案
- 若清除后服务异常：从备份恢复 `mysqldump` 文件
- 若误导入 mock 数据：重新执行 clean-mock.ps1
