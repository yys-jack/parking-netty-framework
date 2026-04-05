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

INSERT INTO auth_info(serial,park_code,secret,enabled) VALUES ('TEST-001', 'PARK001', 'test-secret', 1);

-- Space snapshot table for parking space status tracking
CREATE TABLE IF NOT EXISTS space_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键 ID',
    park_code VARCHAR(32) NOT NULL COMMENT '停车场编码',
    total_spaces INT NOT NULL COMMENT '总车位数',
    remained_spaces INT NOT NULL COMMENT '剩余车位数',
    occupancy_rate DECIMAL(5,2) COMMENT ' occupancy 率',
    space_time TIMESTAMP NOT NULL COMMENT '车位时间',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_park_code (park_code),
    INDEX idx_space_time (space_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车位快照表';
