-- liquibase formatted sql

-- changeset galaxyxieyu:202604101100
CREATE TABLE ai_agent_debug_event (
    id BIGINT AUTO_INCREMENT COMMENT '主键ID' PRIMARY KEY,
    mac_address VARCHAR(64) COMMENT 'MAC地址',
    device_id VARCHAR(64) COMMENT '设备ID',
    agent_id VARCHAR(32) COMMENT '智能体ID',
    session_id VARCHAR(64) COMMENT '会话ID',
    turn_id VARCHAR(64) COMMENT '轮次ID',
    event_source VARCHAR(32) COMMENT '事件来源',
    event_type VARCHAR(64) COMMENT '事件类型',
    direction VARCHAR(16) COMMENT '事件方向',
    origin VARCHAR(32) COMMENT '内容来源归因',
    summary_text VARCHAR(512) COMMENT '摘要文本',
    payload_json LONGTEXT COMMENT '结构化载荷',
    sentence_id VARCHAR(64) COMMENT 'TTS句子ID',
    request_id VARCHAR(64) COMMENT '请求ID',
    speaker VARCHAR(128) COMMENT '说话人',
    runtime_account VARCHAR(128) COMMENT '运行时账号',
    status VARCHAR(32) COMMENT '状态',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_ai_agent_debug_event_agent_session_created (agent_id, session_id, created_at),
    INDEX idx_ai_agent_debug_event_device_created (device_id, created_at),
    INDEX idx_ai_agent_debug_event_turn_created (turn_id, created_at),
    INDEX idx_ai_agent_debug_event_source_status (event_source, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='智能体调试事件时间线表';
