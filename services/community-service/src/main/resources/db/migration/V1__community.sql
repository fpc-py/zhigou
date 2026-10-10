CREATE TABLE community_note (
    id BIGINT NOT NULL COMMENT '笔记ID（Snowflake）',
    author_id BIGINT NOT NULL COMMENT '作者用户ID',
    author_name VARCHAR(64) NOT NULL COMMENT '作者昵称',
    spu_id BIGINT DEFAULT NULL COMMENT '关联SPU商品',
    title VARCHAR(60) NOT NULL COMMENT '标题',
    content TEXT NOT NULL COMMENT '正文',
    images JSON DEFAULT NULL COMMENT '图片URL列表',
    like_count INT NOT NULL DEFAULT 0,
    favorite_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    fake_flag TINYINT NOT NULL DEFAULT 0 COMMENT '1=疑似营销/重复',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0=正常 1=隐藏',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_author (author_id),
    KEY idx_created (created_at),
    KEY idx_spu (spu_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='种草笔记';

CREATE TABLE community_comment (
    id BIGINT NOT NULL COMMENT '评论ID',
    note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    author_name VARCHAR(64) NOT NULL,
    content VARCHAR(500) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_note (note_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='笔记评论';

CREATE TABLE community_interaction (
    id BIGINT NOT NULL COMMENT '互动ID',
    note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    type TINYINT NOT NULL COMMENT '1=点赞 2=收藏',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_note_user_type (note_id, user_id, type),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点赞/收藏';

-- dev 种子：3 条种草笔记（关联真实 SPU，内容基于真实商品属性描述）
INSERT INTO community_note (id, author_id, author_name, spu_id, title, content, images, like_count, favorite_count, comment_count, fake_flag, status, created_at)
VALUES
(9000000000000000201, 2107757313435291648, '测试用户甲', 9000000000000000011, '蓝牙耳机真实体验：降噪续航都在线', '用了两周的蓝牙耳机，通勤降噪效果不错，续航单次能用挺久。音质在这个价位段算扎实，佩戴也稳。日常通勤、运动都合适，预算有限的学生党可以考虑。', '[]', 12, 5, 2, 0, 0, DATE_SUB(NOW(), INTERVAL 2 HOUR)),
(9000000000000000202, 2108780534158835712, '测试用户乙', 9000000000000000010, '轻量运动鞋开箱：脚感轻便适合日常', '这双运动鞋主打轻量，上脚确实比之前的鞋轻不少。日常通勤和慢跑都能穿，鞋底回弹还行，透气性OK。尺码正常，按平时码买就行。', '[]', 8, 3, 1, 0, 0, DATE_SUB(NOW(), INTERVAL 1 HOUR)),
(9000000000000000203, 2107757313435291648, '测试用户甲', 9000000000000000012, '棉质T恤百搭实测：基础款也能穿出层次', '基础款棉质T恤，面料软，洗了几次没变形没起球。版型偏宽松，单穿或叠穿都行。颜色百搭，衣柜必备款。', '[]', 6, 2, 0, 0, 0, DATE_SUB(NOW(), INTERVAL 30 MINUTE));
