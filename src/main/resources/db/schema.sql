-- ============================================================
-- AI 学习助手 · 基础数据库（MySQL 8.0，utf8mb4）
-- 说明：应用启动时会自动执行本脚本（表不存在时创建并初始化）
--       也可手动执行：mysql -u root -p < schema.sql
-- ============================================================

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username    VARCHAR(50)  NOT NULL COMMENT '登录账号',
    password    VARCHAR(128) NOT NULL COMMENT 'SHA-256(salt + 明文)',
    salt        VARCHAR(32)  NOT NULL COMMENT '密码盐',
    nickname    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    role        VARCHAR(20)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_subject (
    id          INT         NOT NULL AUTO_INCREMENT COMMENT '学科ID',
    name        VARCHAR(50) NOT NULL COMMENT '学科名称',
    code        VARCHAR(30) NOT NULL COMMENT '学科编码',
    sort_no     INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_subject_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学科表';

CREATE TABLE IF NOT EXISTS t_category (
    id          INT         NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    subject_id  INT         NOT NULL COMMENT '所属学科',
    parent_id   INT         NOT NULL DEFAULT 0 COMMENT '父分类，0为一级',
    name        VARCHAR(80) NOT NULL COMMENT '分类/知识点名称',
    description VARCHAR(255) DEFAULT NULL,
    sort_no     INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category_subject (subject_id),
    KEY idx_category_parent (parent_id),
    CONSTRAINT fk_category_subject FOREIGN KEY (subject_id) REFERENCES t_subject (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='题目分类（知识点）表';

CREATE TABLE IF NOT EXISTS t_upload (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL COMMENT '上传用户',
    original_name VARCHAR(255) DEFAULT NULL COMMENT '原始文件名',
    stored_path   VARCHAR(255) NOT NULL COMMENT '可访问的相对路径',
    content_type  VARCHAR(100) DEFAULT NULL,
    file_size     BIGINT       NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_upload_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='图片上传记录表';

CREATE TABLE IF NOT EXISTS t_question (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    user_id     BIGINT       NOT NULL COMMENT '录入用户',
    subject_id  INT          NOT NULL COMMENT '学科',
    category_id INT          DEFAULT NULL COMMENT '分类/知识点',
    title       VARCHAR(200) NOT NULL COMMENT '题目标题/摘要',
    content     TEXT         COMMENT '题干文本',
    image_path  VARCHAR(255) DEFAULT NULL COMMENT '题目图片路径',
    q_type      VARCHAR(20)  NOT NULL DEFAULT 'essay' COMMENT '题型：choice选择/judge判断/blank填空/short简答/essay解答',
    options     TEXT         COMMENT '选择题选项，JSON 数组，如 ["A. 1","B. 2"]',
    correct_answer TEXT      COMMENT '标准答案：选择题为选项字母，判断题为正确/错误，其余为参考答案',
    answer      TEXT         COMMENT '学生作答',
    analysis    TEXT         COMMENT '解析/错因分析',
    source      VARCHAR(100) DEFAULT NULL COMMENT '来源',
    difficulty  TINYINT      NOT NULL DEFAULT 2 COMMENT '1易 2中 3难',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1正常 0删除',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_question_user (user_id),
    KEY idx_question_subject (subject_id),
    KEY idx_question_category (category_id),
    CONSTRAINT fk_question_subject FOREIGN KEY (subject_id) REFERENCES t_subject (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='错题/题目表';

-- AI 调用日志：记录每次问答的场景、模型与耗时，便于回溯与成本观察
CREATE TABLE IF NOT EXISTS t_ai_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL COMMENT '用户ID',
    question_id BIGINT       DEFAULT NULL COMMENT '关联题目ID',
    scene       VARCHAR(32)  NOT NULL COMMENT 'explain/advice/ask',
    provider    VARCHAR(32)  DEFAULT NULL COMMENT 'deepseek/ollama/local',
    model       VARCHAR(80)  DEFAULT NULL COMMENT '模型名',
    prompt      MEDIUMTEXT   COMMENT '提示词',
    answer      MEDIUMTEXT   COMMENT '模型回答',
    elapsed_ms  INT          NOT NULL DEFAULT 0 COMMENT '耗时毫秒',
    success     TINYINT      NOT NULL DEFAULT 1 COMMENT '1成功 0失败',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_ai_user_time (user_id, created_at),
    KEY idx_ai_question (question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用日志';

-- 全局设置：设置页写入的覆盖项，优先于 ai.properties；为空表示沿用配置文件
-- 敏感值（如云端 API Key）以 enc:v1: 前缀的 AES-GCM 密文存储
CREATE TABLE IF NOT EXISTS t_setting (
    setting_key   VARCHAR(64)  NOT NULL COMMENT '配置键，与 ai.properties 同名',
    setting_value TEXT         COMMENT '配置值，敏感项为密文',
    updated_by    BIGINT       DEFAULT NULL COMMENT '最后修改人',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (setting_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='全局设置（覆盖 ai.properties）';

-- 学习总结快照：每个用户一份最新总结，只有主动生成时才覆盖
CREATE TABLE IF NOT EXISTS t_summary (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    user_id        BIGINT       NOT NULL COMMENT '用户ID',
    text           MEDIUMTEXT   COMMENT 'AI 总结正文',
    stats_json     MEDIUMTEXT   COMMENT '生成时的统计数据快照(JSON)',
    provider       VARCHAR(32)  DEFAULT NULL COMMENT 'deepseek/ollama/local',
    model          VARCHAR(80)  DEFAULT NULL COMMENT '模型名',
    provider_label VARCHAR(64)  DEFAULT NULL COMMENT '展示用的提供方名称',
    elapsed_ms     INT          NOT NULL DEFAULT 0 COMMENT '生成耗时毫秒',
    fallback       TINYINT      NOT NULL DEFAULT 0 COMMENT '1=本地规则生成',
    log_id         BIGINT       DEFAULT NULL COMMENT '对应 t_ai_log.id',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_summary_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习总结快照';
