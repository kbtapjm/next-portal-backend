CREATE TABLE portal_permission (
    code VARCHAR(80) NOT NULL PRIMARY KEY,
    description VARCHAR(200) NOT NULL
);

CREATE TABLE portal_role (
    code VARCHAR(50) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    permission_revision BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE portal_role_permission (
    role_code VARCHAR(50) NOT NULL,
    permission_code VARCHAR(80) NOT NULL,
    PRIMARY KEY (role_code, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_code) REFERENCES portal_role (code),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_code) REFERENCES portal_permission (code)
);

CREATE TABLE portal_member_role (
    member_id BIGINT NOT NULL,
    role_code VARCHAR(50) NOT NULL,
    PRIMARY KEY (member_id, role_code),
    CONSTRAINT fk_member_role_member FOREIGN KEY (member_id) REFERENCES portal_member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_role_role FOREIGN KEY (role_code) REFERENCES portal_role (code)
);
CREATE INDEX ix_member_role_role ON portal_member_role (role_code, member_id);

INSERT INTO portal_permission (code, description) VALUES
    ('admin:access', '관리자 시스템 로그인 및 접근'),
    ('user:read', '사용자 목록 및 상세 조회'),
    ('user:write', '사용자 생성 및 기본 정보·상태 변경'),
    ('role:read', '역할 및 권한 카탈로그 조회'),
    ('role:manage', '역할 생성·수정·삭제 및 사용자 역할 부여');

INSERT INTO portal_role (code, name, system_role) VALUES
    ('ADMIN', '시스템 관리자', TRUE),
    ('USER', '일반 사용자', TRUE),
    ('USER_VIEWER', '사용자 조회 담당자', FALSE),
    ('USER_MANAGER', '사용자 관리 담당자', FALSE);

INSERT INTO portal_role_permission (role_code, permission_code)
    SELECT 'ADMIN', code FROM portal_permission;
INSERT INTO portal_role_permission (role_code, permission_code) VALUES
    ('USER_VIEWER', 'admin:access'), ('USER_VIEWER', 'user:read'),
    ('USER_MANAGER', 'admin:access'), ('USER_MANAGER', 'user:read'), ('USER_MANAGER', 'user:write');

-- Preserve every existing assignment before retiring the single-role column.
INSERT INTO portal_member_role (member_id, role_code) SELECT id, role FROM portal_member;
ALTER TABLE portal_member DROP CONSTRAINT ck_portal_member_role;
ALTER TABLE portal_member DROP COLUMN role;

-- Sessions issued under the old authorization model must sign in again.
UPDATE portal_member SET auth_version = auth_version + 1, version = version + 1;
