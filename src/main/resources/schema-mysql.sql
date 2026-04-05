-- Authentication info table (MySQL specific)
-- Production deployment script for MySQL
CREATE TABLE IF NOT EXISTS auth_info (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    serial VARCHAR(64) NOT NULL UNIQUE COMMENT '设备序列号',
    park_code VARCHAR(64) NOT NULL COMMENT '停车场编码',
    secret VARCHAR(128) NOT NULL COMMENT '密钥',
    enabled TINYINT(1) DEFAULT 1 COMMENT '是否启用：1-启用，0-禁用',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_serial (serial),
    INDEX idx_park_code (park_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证信息表';
