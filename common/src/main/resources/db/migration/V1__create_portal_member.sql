CREATE TABLE portal_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(254) NOT NULL,
    name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    auth_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT uk_portal_member_email UNIQUE (email),
    CONSTRAINT ck_portal_member_role CHECK (role IN ('ADMIN', 'USER')),
    CONSTRAINT ck_portal_member_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);
