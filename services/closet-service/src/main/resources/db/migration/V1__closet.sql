-- 智能衣橱 + 家居管理（演示数据：衣物/家居为示例口径，供穿搭规则与补货清单演示）
CREATE TABLE IF NOT EXISTS closet_item (
    id           BIGINT       NOT NULL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    name         VARCHAR(128) NOT NULL,
    category     VARCHAR(32)  DEFAULT '上装' COMMENT '上装/下装/外套/鞋履/配饰',
    season       VARCHAR(16)  DEFAULT '四季' COMMENT '春/夏/秋/冬/四季',
    color        VARCHAR(32)  DEFAULT '',
    image_url    VARCHAR(255) DEFAULT '',
    tags         VARCHAR(255) DEFAULT '',
    wear_count   INT          DEFAULT 0,
    last_worn_at DATETIME     DEFAULT NULL,
    status       TINYINT      DEFAULT 1 COMMENT '1=在册 0=已移除',
    deleted      TINYINT      DEFAULT 0,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='衣橱衣物（演示）';

CREATE TABLE IF NOT EXISTS outfit_plan (
    id          BIGINT       NOT NULL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(128) DEFAULT '',
    occasion    VARCHAR(32)  DEFAULT '通勤',
    item_ids    VARCHAR(512) DEFAULT '[]' COMMENT 'JSON 衣物 id 列表',
    score       INT          DEFAULT 0,
    deleted     TINYINT      DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='穿搭方案（规则生成）';

CREATE TABLE IF NOT EXISTS home_asset (
    id               BIGINT       NOT NULL PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    name             VARCHAR(128) NOT NULL,
    category         VARCHAR(32)  DEFAULT '日用品' COMMENT '食品/日用品/家电/清洁',
    quantity         INT          DEFAULT 1,
    unit             VARCHAR(32)  DEFAULT '件',
    expire_at        DATE         DEFAULT NULL,
    replenish_alert  TINYINT      DEFAULT 0,
    deleted          TINYINT      DEFAULT 0,
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='家居盘点（演示）';

-- Seed：用户 13800138001 示例衣物
INSERT INTO closet_item (id, user_id, name, category, season, color, tags, wear_count, last_worn_at) VALUES
(9000000000000000801, 2107757313435291648, '燕麦色风衣', '外套', '春秋', '燕麦色', '通勤,叠穿', 6, '2026-10-05 09:00:00'),
(9000000000000000802, 2107757313435291648, '针织打底衫', '上装', '秋冬', '米白', '打底,保暖', 9, '2026-10-08 09:00:00'),
(9000000000000000803, 2107757313435291648, '直筒西裤', '下装', '四季', '藏青', '通勤,百搭', 12, '2026-10-06 09:00:00'),
(9000000000000000804, 2107757313435291648, '低帮小白鞋', '鞋履', '春夏', '白色', '百搭,通勤', 15, '2026-10-07 09:00:00'),
(9000000000000000805, 2107757313435291648, '羊毛围巾', '配饰', '秋冬', '驼色', '保暖,叠穿', 3, '2026-10-02 09:00:00'),
(9000000000000000806, 2107757313435291648, '基础白T恤', '上装', '夏季', '白色', '基础,内搭', 18, '2026-09-28 09:00:00'),
(9000000000000000807, 2107757313435291648, '深蓝牛仔裤', '下装', '四季', '深蓝', '休闲,耐穿', 10, '2026-10-03 09:00:00'),
(9000000000000000808, 2107757313435291648, '轻量跑鞋', '鞋履', '四季', '灰色', '运动,轻量', 8, '2026-09-30 09:00:00');

-- Seed：家居盘点（含低量/临期触发补货）
INSERT INTO home_asset (id, user_id, name, category, quantity, unit, expire_at) VALUES
(9000000000000000901, 2107757313435291648, '东北大米5kg', '食品', 1, '袋', '2027-03-01'),
(9000000000000000902, 2107757313435291648, '纯牛奶250ml', '食品', 6, '盒', '2026-10-15'),
(9000000000000000903, 2107757313435291648, '洗衣液3L', '日用品', 1, '瓶', NULL),
(9000000000000000904, 2107757313435291648, '抽纸', '日用品', 3, '提', NULL),
(9000000000000000905, 2107757313435291648, '洗洁精', '清洁', 1, '瓶', NULL),
(9000000000000000906, 2107757313435291648, '柠檬味清洁剂', '清洁', 2, '瓶', '2026-10-12');
