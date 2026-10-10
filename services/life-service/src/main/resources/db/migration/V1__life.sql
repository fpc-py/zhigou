-- 本地生活：POI 门店/商圈 + 服务 SKU + 到店预约（演示数据：真实商圈名称，营业信息为演示口径）
CREATE TABLE IF NOT EXISTS poi_store (
    id              BIGINT       NOT NULL PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL COMMENT '商圈/餐饮/生鲜/家政/到店',
    address         VARCHAR(255) DEFAULT '',
    distance_m      INT          DEFAULT 0,
    avg_price_fen   BIGINT       DEFAULT 0,
    rating          DECIMAL(2,1) DEFAULT 5.0,
    cover_url       VARCHAR(255) DEFAULT '',
    hours           VARCHAR(64)  DEFAULT '',
    tags            VARCHAR(255) DEFAULT '',
    business_status TINYINT      DEFAULT 1 COMMENT '1=营业 0=休息',
    lat             VARCHAR(32)  DEFAULT '',
    lng             VARCHAR(32)  DEFAULT '',
    deleted         TINYINT      DEFAULT 0,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='POI 门店/商圈（演示）';

CREATE TABLE IF NOT EXISTS life_sku (
    id          BIGINT       NOT NULL PRIMARY KEY,
    store_id    BIGINT       DEFAULT NULL,
    name        VARCHAR(128) NOT NULL,
    category    VARCHAR(32)  NOT NULL COMMENT '外卖/生鲜/家政/到店券',
    price_fen   BIGINT       DEFAULT 0,
    unit        VARCHAR(32)  DEFAULT '',
    stock       INT          DEFAULT 0,
    cover_url   VARCHAR(255) DEFAULT '',
    description VARCHAR(255) DEFAULT '',
    status      TINYINT      DEFAULT 1,
    deleted     TINYINT      DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='本地生活服务 SKU（演示）';

CREATE TABLE IF NOT EXISTS life_appointment (
    id                BIGINT       NOT NULL PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    store_id          BIGINT       NOT NULL,
    sku_id            BIGINT       DEFAULT NULL,
    appointment_time  VARCHAR(64)  DEFAULT '',
    remark            VARCHAR(255) DEFAULT '',
    status            TINYINT      DEFAULT 0 COMMENT '0=待确认 1=已确认 2=已完成 3=已取消',
    deleted           TINYINT      DEFAULT 0,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='到店/服务预约单';

-- Seed：成都真实商圈/门店（演示数据，价格为演示口径）
INSERT INTO poi_store (id, name, category, address, distance_m, avg_price_fen, rating, cover_url, hours, tags, business_status) VALUES
(9000000000000000501, '春熙路商圈', '商圈', '成都市锦江区春熙路', 800, 0, 4.9, '', '全天开放', '潮流地标,太古里,IFS', 1),
(9000000000000000502, '宽窄巷子', '商圈', '成都市青羊区长顺上街', 3200, 0, 4.8, '', '全天开放', '历史街区,网红打卡,美食', 1),
(9000000000000000503, '锦里古街', '商圈', '成都市武侯区武侯祠大街', 6100, 0, 4.7, '', '全天开放', '三国文化,小吃街,夜景', 1),
(9000000000000000504, '交子公园商圈', '商圈', '成都市高新区天府大道北段', 9200, 0, 4.8, '', '全天开放', 'SKP,会展,商务休闲', 1),
(9000000000000000511, '蜀大侠火锅（宽窄巷子店）', '餐饮', '成都市青羊区宽窄巷子旁', 3100, 12000, 4.7, '', '11:00-23:00', '火锅,排队,双人餐', 1),
(9000000000000000512, '冒椒火辣串串（春熙路店）', '餐饮', '成都市锦江区春熙路东段', 950, 6800, 4.6, '', '10:30-22:30', '串串,平价,学生友好', 1),
(9000000000000000521, '盒马鲜生（春熙路店）', '生鲜', '成都市锦江区红星路三段', 1100, 0, 4.8, '', '08:00-22:00', '生鲜,即时配送,烘焙', 1),
(9000000000000000522, '永辉超市（交子大道店）', '生鲜', '成都市高新区交子大道', 9500, 0, 4.6, '', '08:00-22:00', '超市,水果,日用品', 1),
(9000000000000000531, '天鹅到家（成都演示站）', '家政', '成都市武侯区簇桥', 7800, 0, 4.5, '', '08:00-20:00', '保洁,家电清洗,演示', 1),
(9000000000000000532, '叮咚家政（高新演示站）', '家政', '成都市高新区天府三街', 8600, 0, 4.4, '', '08:00-20:00', '小时工,收纳,演示', 1);

INSERT INTO life_sku (id, store_id, name, category, price_fen, unit, stock, description) VALUES
(9000000000000000601, 9000000000000000512, '冒椒火辣双人串串套餐', '外卖', 8800, '2人餐', 100, '含50根签+锅底+2杯饮品，演示商品'),
(9000000000000000602, 9000000000000000512, '冷锅串串单人份', '外卖', 3900, '1人份', 200, '免配送费，30分钟达（演示）'),
(9000000000000000611, 9000000000000000521, '有机蔬菜组合包', '生鲜', 2990, '1袋约2kg', 80, '当日采摘，次日达（演示）'),
(9000000000000000612, 9000000000000000521, '智利进口车厘子JJ', '生鲜', 4990, '500g', 60, '冷链直达（演示）'),
(9000000000000000621, 9000000000000000531, '4小时深度保洁', '家政', 25900, '次', 10, '含厨房卫生间，演示预约'),
(9000000000000000622, 9000000000000000532, '空调挂机深度清洗', '家政', 9900, '台', 20, '高温蒸汽消毒，演示预约'),
(9000000000000000631, 9000000000000000511, '蜀大侠双人火锅餐券', '到店券', 19900, '2人餐', 50, '含锅底+8份菜，免排队，演示'),
(9000000000000000632, 9000000000000000501, '春熙路商圈逛逛券', '到店券', 0, '张', 999, '商圈到店打卡引导，演示');

-- Seed：示例预约（用户 13800138001）
INSERT INTO life_appointment (id, user_id, store_id, sku_id, appointment_time, remark, status) VALUES
(9000000000000000701, 2107757313435291648, 9000000000000000511, 9000000000000000631, '2026-10-12 18:30', '2人，靠窗座位', 1);
