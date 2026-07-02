-- ============================================================
-- 转发平台初始化脚本
-- 配置表 + 按天 RANGE 分区的日志/明细表
-- ============================================================

-- 数据源（可复用的 MQTT/Kafka 连接）
CREATE TABLE data_source (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL UNIQUE,
    type        VARCHAR(16)  NOT NULL,           -- MQTT / KAFKA
    config      JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 输出目标（可复用的 MQTT/Kafka/HTTP 目标）
CREATE TABLE output_target (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL UNIQUE,
    type        VARCHAR(16)  NOT NULL,           -- MQTT / KAFKA / HTTP
    config      JSONB        NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 转发协议
CREATE TABLE protocol (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(128) NOT NULL UNIQUE,
    description         VARCHAR(512),
    status              VARCHAR(16)  NOT NULL DEFAULT 'STOPPED',  -- STOPPED / RUNNING / ERROR / STARTING
    status_message      VARCHAR(1024),
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    source_id           BIGINT       REFERENCES data_source(id),
    output_target_id    BIGINT       REFERENCES output_target(id),
    current_version_id  BIGINT,
    ring_buffer_size    INT          NOT NULL DEFAULT 16384,
    worker_threads      INT          NOT NULL DEFAULT 4,
    log_retention_days  INT          NOT NULL DEFAULT 7,
    sample_rate         DOUBLE PRECISION NOT NULL DEFAULT 1.0,   -- 明细记录采样率 0~1
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 脚本版本（支持回滚）
CREATE TABLE script_version (
    id             BIGSERIAL PRIMARY KEY,
    protocol_id    BIGINT NOT NULL REFERENCES protocol(id) ON DELETE CASCADE,
    version        INT    NOT NULL,
    language       VARCHAR(16) NOT NULL DEFAULT 'groovy',
    code           TEXT   NOT NULL,
    compile_status VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',       -- OK / ERROR / UNKNOWN
    compile_error  TEXT,
    remark         VARCHAR(512),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (protocol_id, version)
);

ALTER TABLE protocol
    ADD CONSTRAINT fk_protocol_current_version
    FOREIGN KEY (current_version_id) REFERENCES script_version(id);

CREATE INDEX idx_protocol_enabled ON protocol(enabled);
CREATE INDEX idx_script_version_protocol ON script_version(protocol_id);

-- ============================================================
-- 运行/状态/异常日志：按天 RANGE 分区
-- 具体日分区由应用启动及定时任务动态创建
-- ============================================================
CREATE TABLE protocol_log (
    id           BIGSERIAL,
    protocol_id  BIGINT      NOT NULL,
    level        VARCHAR(8)  NOT NULL,           -- INFO / WARN / ERROR / DEBUG
    message      TEXT,
    log_time     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, log_time)
) PARTITION BY RANGE (log_time);

CREATE INDEX idx_protocol_log_pid_time ON protocol_log(protocol_id, log_time DESC);

-- ============================================================
-- 逐条转发明细：按天 RANGE 分区，可采样
-- ============================================================
CREATE TABLE forward_record (
    id           BIGSERIAL,
    protocol_id  BIGINT      NOT NULL,
    input        TEXT,
    output       TEXT,
    success      BOOLEAN     NOT NULL DEFAULT TRUE,
    error        TEXT,
    cost_ms      INT,
    record_time  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id, record_time)
) PARTITION BY RANGE (record_time);

CREATE INDEX idx_forward_record_pid_time ON forward_record(protocol_id, record_time DESC);
