CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE IF NOT EXISTS identity.users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT identity_users_role_check CHECK (role IN ('ADMIN', 'MEDICO', 'ENFERMEIRO', 'PACIENTE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_identity_users_username ON identity.users (username);

INSERT INTO identity.users (id, username, password_hash, role, active)
SELECT source.id, source.username, source.password_hash, source.role, source.active
FROM users source
WHERE NOT EXISTS (SELECT 1 FROM identity.users target WHERE target.id = source.id)
ON CONFLICT DO NOTHING;

DROP TABLE users;
