-- 内容社区二期：短视频（图文形态 MVP）+ 直播
-- 说明：短视频 MVP 采用竖屏图文卡片形态（video_url 存图文物料，生产接入真实视频仅换 URL，不涉及表结构变更）

CREATE TABLE community_video (
    id BIGINT NOT NULL COMMENT '视频ID（Snowflake）',
    author_id BIGINT NOT NULL COMMENT '作者用户ID',
    author_name VARCHAR(64) NOT NULL COMMENT '作者昵称',
    spu_id BIGINT DEFAULT NULL COMMENT '关联SPU商品',
    title VARCHAR(60) NOT NULL COMMENT '标题',
    content VARCHAR(500) DEFAULT NULL COMMENT '文案',
    video_url VARCHAR(255) NOT NULL COMMENT '视频URL（MVP为竖屏图文物料URL，生产接真实视频）',
    cover_url VARCHAR(255) DEFAULT NULL COMMENT '封面URL',
    duration_sec INT NOT NULL DEFAULT 0 COMMENT '时长秒（图文形态为轮播时长）',
    like_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    view_count INT NOT NULL DEFAULT 0,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=隐藏',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_author (author_id),
    KEY idx_created (created_at),
    KEY idx_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短视频（图文MVP）';

CREATE TABLE community_live (
    id BIGINT NOT NULL COMMENT '直播ID（Snowflake）',
    author_id BIGINT NOT NULL COMMENT '主播用户ID',
    author_name VARCHAR(64) NOT NULL COMMENT '主播昵称',
    spu_id BIGINT DEFAULT NULL COMMENT '主推商品SPU',
    title VARCHAR(60) NOT NULL COMMENT '直播标题',
    cover_url VARCHAR(255) DEFAULT NULL COMMENT '封面URL',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=预告 1=直播中 2=已结束',
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    scheduled_start DATETIME DEFAULT NULL COMMENT '预告开播时间',
    started_at DATETIME DEFAULT NULL,
    ended_at DATETIME DEFAULT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status (status, created_at),
    KEY idx_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='直播';

-- 短视频 seed（3 条图文短视频，关联真实 SPU，图文物料复用 MinIO 已有图片）
INSERT INTO community_video (id, author_id, author_name, spu_id, title, content, video_url, cover_url, duration_sec, like_count, favorite_count, comment_count, view_count, status, created_at)
VALUES
(9000000000000000301, 2107757313435291648, '测试用户甲', 9000000000000000011, '通勤降噪实测｜蓝牙耳机', '地铁报站声一键安静，音质同价位能打，通勤党看这里。', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 6, 18, 7, 3, 1024, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(9000000000000000302, 2108780534158835712, '测试用户乙', 9000000000000000010, '轻量运动鞋开箱｜上脚轻得离谱', '单只不到200g？日常通勤慢跑都合适，尺码正常。', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 8, 11, 4, 2, 688, 0, DATE_SUB(NOW(), INTERVAL 40 MINUTE)),
(9000000000000000303, 2107757313435291648, '测试用户甲', 9000000000000000012, '基础T恤的3种叠穿', '衣柜必备款，单穿叠穿都能打，学生党配色指南。', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 7, 9, 2, 1, 425, 0, DATE_SUB(NOW(), INTERVAL 20 MINUTE));

-- 直播 seed（1 直播中 + 1 预告）
INSERT INTO community_live (id, author_id, author_name, spu_id, title, cover_url, status, view_count, like_count, scheduled_start, started_at, created_at)
VALUES
(9000000000000000401, 2107757313435291648, '智友1648', 9000000000000000011, '蓝牙耳机专场｜限时拼团价', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 1, 2356, 189, NULL, DATE_SUB(NOW(), INTERVAL 40 MINUTE), DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(9000000000000000402, 2108780534158835712, '测试用户乙', 9000000000000000010, '运动鞋上新预告｜今晚8点', 'http://localhost:9000/zhigou-media/20261003/2106291426039648256/1cc36f05b4bf4e88bcbb39f9163a46e1.png', 0, 0, 0, DATE_ADD(NOW(), INTERVAL 2 HOUR), NULL, DATE_SUB(NOW(), INTERVAL 30 MINUTE));

