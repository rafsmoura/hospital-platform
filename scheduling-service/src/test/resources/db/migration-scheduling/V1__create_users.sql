CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL,
    CONSTRAINT users_role_check CHECK (role IN ('ADMIN', 'MEDICO', 'ENFERMEIRO', 'PACIENTE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_users_username ON users (username);

INSERT INTO users (id, username, password_hash, role, active)
SELECT '00000000-0000-0000-0000-000000000001', 'admin',
       '$2a$10$.MeF7Pt.KO3giTrisHoE9OaMLngeHY/utKgZVyLd1ABUdaxVozUee', 'ADMIN', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');

INSERT INTO users (id, username, password_hash, role, active)
SELECT '00000000-0000-0000-0000-000000000002', 'medico',
       '$2a$10$.MeF7Pt.KO3giTrisHoE9OaMLngeHY/utKgZVyLd1ABUdaxVozUee', 'MEDICO', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'medico');

INSERT INTO users (id, username, password_hash, role, active)
SELECT '00000000-0000-0000-0000-000000000003', 'enfermeiro',
       '$2a$10$.MeF7Pt.KO3giTrisHoE9OaMLngeHY/utKgZVyLd1ABUdaxVozUee', 'ENFERMEIRO', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'enfermeiro');

INSERT INTO users (id, username, password_hash, role, active)
SELECT '00000000-0000-0000-0000-000000000004', 'paciente',
       '$2a$10$.MeF7Pt.KO3giTrisHoE9OaMLngeHY/utKgZVyLd1ABUdaxVozUee', 'PACIENTE', TRUE
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'paciente');
