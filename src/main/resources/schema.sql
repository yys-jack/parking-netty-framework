-- Authentication info table (H2 compatible)
-- For MySQL production use: use schema-mysql.sql
CREATE TABLE IF NOT EXISTS auth_info (
    id INT PRIMARY KEY AUTO_INCREMENT,
    serial VARCHAR(64) NOT NULL UNIQUE,
    park_code VARCHAR(64) NOT NULL,
    secret VARCHAR(128) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_serial ON auth_info(serial);
CREATE INDEX IF NOT EXISTS idx_park_code ON auth_info(park_code);

-- Insert test data (H2 compatible)
MERGE INTO auth_info (serial, park_code, secret, enabled) KEY (serial) VALUES ('TEST-001', 'PARK001', 'test-secret', TRUE);
