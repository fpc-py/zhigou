# 智购 · Mock 数据导入与上线清除指南

> 本文档说明测试/演示时如何导入 mock 数据，以及上线时如何快速切换为干净状态。

---

## 一、机制总览

采用 **Flyway Profile 分离** 方案：

| 环境 | 启动方式 | 执行的迁移 | 是否含 mock |
|---|---|---|---|
| 测试 / 演示 | `--spring.profiles.active=dev` | `db/migration` + `db/mock` | 是 |
| 上线 / 生产 | 默认（不带 dev） | `db/migration` | 否 |

- **DDL + 基础种子数据**：`src/main/resources/db/migration/V*.sql`（所有环境都执行）
- **Mock 数据**：`src/main/resources/db/mock/V20261090__mock_*.sql`（仅 dev profile 执行）

主 `application.yml` 的 flyway locations 固定为 `classpath:db/migration`；
`application-dev.yml` 覆盖为 `classpath:db/migration,classpath:db/mock`。

> ⚠️ **重要**：`db/mock` 必须与 `db/migration` **平级**，不能放在 `db/migration/dev` 下。
> 因为 Flyway 对 `classpath:` 位置的扫描是**递归**的，若 mock 放在 `db/migration` 子目录内，
> 即使不激活 dev profile 也会被扫描并执行（上线时会误导入 mock）。

版本号 `V20261090` 高于所有现有 DDL 迁移（`V20261001`~`V20261008`），确保 mock 在 DDL 之后执行。

---

## 二、测试 / 演示：导入 Mock 数据

### 2.1 启动单个服务（带 mock）

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
java -jar services/product-service/target/zhigou-product-service-0.1.0-SNAPSHOT.jar
```

或直接用启动参数：

```powershell
java -jar services/product-service/target/zhigou-product-service-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 2.2 启动全部服务

对 11 个服务分别带上 `--spring.profiles.active=dev` 启动即可（端口见下表）。

### 2.3 验证 Mock 已导入

```powershell
# 检查 Flyway 历史记录出现 V20261090
docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_product `
  -e "SELECT version, success FROM flyway_product_history WHERE version='20261090';"

# 检查 mock 商品（spu_id 以 9000 开头）
docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_product `
  -e "SELECT spu_id, name FROM product_spu WHERE spu_id LIKE '9%';"
```

### 2.4 Mock 数据 ID 约定

所有 mock 数据使用固定 ID 段 `9000000000000000xxx`，避开 Snowflake 真实 ID，便于识别与清除：

| 数据类型 | ID 范围 | 示例 |
|---|---|---|
| 用户 user_id | 9000000000000000001 ~ 003 | 测试用户 01/02/03 |
| SPU spu_id | 9000000000000000010 ~ 012 | 运动鞋 / 耳机 / T恤 |
| SKU sku_id | 9000000000000000020 ~ 025 | 各颜色规格 |
| 订单 order_id | 9000000000000000100 ~ 102 | 示例订单 |
| 售后单 aftersale_no | MOCK_AS_001 ~ 003 | 示例售后单 |
| 支付单 payment_no | MOCK_PAY_001 ~ 002 | 示例支付记录 |
| 运单 shipment_no | MOCK_SHIP_001 ~ 002 | 示例运单 |
| 文件 file_id | MOCK_FILE_001 ~ 003 | 示例文件元数据 |

---

## 三、上线：清除 Mock 数据

### 3.1 一键清除脚本

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1
```

脚本行为：
1. 交互确认（避免误执行）
2. 对 10 个业务库执行 `TRUNCATE`（保留表结构，重置自增 ID）
3. 清除 Redis 购物车 key（`cart:*`）

跳过确认（CI / 自动化）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1 -SkipConfirm
```

### 3.2 清除范围

| 库 | 清空的表 |
|---|---|
| zhigou_auth | user |
| zhigou_user | user, address |
| zhigou_file | file_meta |
| zhigou_product | product_spu, product_sku, category, brand |
| zhigou_order | order_main, order_item, outbox |
| zhigou_inventory | stock |
| zhigou_payment | payment |
| zhigou_marketing | user_coupon, discount_snapshot, coupon_template, promotion_rule |
| zhigou_logistics | track_event, shipment, freight_template |
| zhigou_aftersale | aftersale_order |

> 说明：清除范围包含 DDL 迁移里自带的种子数据（category / brand / coupon_template / freight_template），
> 上线时应一并清除，由运营重新录入正式基础数据。

---

## 四、上线检查清单（推荐 7 步）

1. **备份数据库**（重要！）：
   ```powershell
   docker exec zhigou-mysql sh -c "exec mysqldump -uroot -p123456 --all-databases" > backup_$(Get-Date -Format yyyyMMdd_HHmmss).sql
   ```
2. **确认启动命令不带** `--spring.profiles.active=dev`
3. **执行清除脚本**：
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts/mock-data/clean-mock.ps1
   ```
4. **验证清除结果**（应全部为 0）：
   ```powershell
   docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_product -e "SELECT COUNT(*) AS mock_spu FROM product_spu WHERE spu_id LIKE '9%';"
   docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_user    -e "SELECT COUNT(*) AS users FROM user;"
   docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_order   -e "SELECT COUNT(*) AS orders FROM order_main;"
   ```
5. **启动所有服务**（不带 dev profile）
6. **验证 Flyway 未导入 mock**：
   ```powershell
   docker exec zhigou-mysql mysql -uroot -p123456 -D zhigou_product `
     -e "SELECT COUNT(*) AS mock_migration FROM flyway_product_history WHERE version='20261090';"
   # 期望结果：0
   ```
7. **跑 e2e 冒烟测试**，确认功能正常：
   ```bash
   bash scripts/e2e-order.sh
   ```

---

## 五、回滚预案

| 场景 | 处理方式 |
|---|---|
| 清除后服务异常 | 从步骤 1 的 `mysqldump` 备份文件恢复 |
| 误导入 mock 数据 | 重新执行 `clean-mock.ps1` |
| dev profile 误带到生产 | 重启服务不带 `--spring.profiles.active=dev`，再执行 `clean-mock.ps1` |

数据库恢复示例：

```powershell
Get-Content backup_20261003_120000.sql | docker exec -i zhigou-mysql mysql -uroot -p123456
```

---

## 六、服务端口对照

| 服务 | 端口 | 库 | mock 文件 |
|---|---|---|---|
| auth-center | 8080 | zhigou_auth | V20261090__mock_auth.sql |
| user-service | 8081 | zhigou_user | V20261090__mock_user.sql |
| file-service | 8082 | zhigou_file | V20261090__mock_file.sql |
| product-service | 8083 | zhigou_product | V20261090__mock_product.sql |
| cart-service | 8084 | (Redis) | V20261090__mock_cart.sql（占位） |
| order-service | 8085 | zhigou_order | V20261090__mock_order.sql |
| inventory-service | 8086 | zhigou_inventory | V20261090__mock_inventory.sql |
| payment-service | 8087 | zhigou_payment | V20261090__mock_payment.sql |
| marketing-service | 8088 | zhigou_marketing | V20261090__mock_marketing.sql |
| logistics-service | 8089 | zhigou_logistics | V20261090__mock_logistics.sql |
| aftersale-service | 8090 | zhigou_aftersale | V20261090__mock_aftersale.sql |

---

## 七、注意事项

1. **不要修改已执行过的 `V20261001`~`V20261008`**：Flyway 已记录其 checksum，修改会导致校验失败。
   新增数据统一用更高版本号（如 `V20261090`）的独立迁移文件。
2. **cart-service 无 DataSource**：纯 Redis 存储，Flyway 自动配置会跳过；其 mock 文件为占位，保持目录结构一致。
3. **`SET FOREIGN_KEY_CHECKS=0`**：清除脚本已包含，避免外键约束阻塞 TRUNCATE。
4. **生产环境禁用** `spring.flyway.clean-on-migrate`：本方案未使用 clean（会删整个 schema），改用可控的 TRUNCATE 脚本。
