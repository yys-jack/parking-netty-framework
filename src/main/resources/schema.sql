-- Authentication info table (H2 compatible)
-- For MySQL production use: use schema-mysql.sql
CREATE TABLE IF NOT EXISTS auth_info (
    id INT PRIMARY KEY AUTO_INCREMENT,
    serial VARCHAR(64) NOT NULL UNIQUE,
    park_code VARCHAR(64) NOT NULL,
    secret VARCHAR(128) NOT NULL,
    enabled int DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_serial ON auth_info(serial);
CREATE INDEX IF NOT EXISTS idx_park_code ON auth_info(park_code);

-- Space snapshot table for parking space status tracking
CREATE TABLE IF NOT EXISTS space_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    park_code VARCHAR(32) NOT NULL,
    total_spaces INT NOT NULL,
    remained_spaces INT NOT NULL,
    occupancy_rate DECIMAL(5,2),
    space_time TIMESTAMP NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_park_code ON space_snapshot(park_code);
CREATE INDEX IF NOT EXISTS idx_space_time ON space_snapshot(space_time);

-- Insert test data (H2 compatible)
MERGE INTO auth_info (serial, park_code, secret, enabled) KEY (serial) VALUES ('TEST-001', 'PARK001', 'test-secret', TRUE);
