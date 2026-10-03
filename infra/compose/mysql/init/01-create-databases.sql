-- ============================================================
-- 智购 · 每个微服务独立数据库（替代共用的 zhigou 库）
-- Docker Compose 启动时自动执行
-- ============================================================

-- 认证中心
CREATE DATABASE IF NOT EXISTS zhigou_auth
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 用户服务
CREATE DATABASE IF NOT EXISTS zhigou_user
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 文件服务
CREATE DATABASE IF NOT EXISTS zhigou_file
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 商品服务
CREATE DATABASE IF NOT EXISTS zhigou_product
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 订单服务
CREATE DATABASE IF NOT EXISTS zhigou_order
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 库存服务
CREATE DATABASE IF NOT EXISTS zhigou_inventory
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 支付服务
CREATE DATABASE IF NOT EXISTS zhigou_payment
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 营销服务
CREATE DATABASE IF NOT EXISTS zhigou_marketing
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 物流服务
CREATE DATABASE IF NOT EXISTS zhigou_logistics
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 售后服务
CREATE DATABASE IF NOT EXISTS zhigou_aftersale
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 购物车服务（纯 Redis，预留数据库）
CREATE DATABASE IF NOT EXISTS zhigou_cart
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;