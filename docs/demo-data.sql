-- Dados de demonstração para ambiente LOCAL (não usar em produção).
-- Cria uma clínica, um médico com horário de segunda a sábado e uma recepcionista.
--
-- Carregar:  docker compose exec -T postgres psql -U medcore -d medcore < docs/demo-data.sql
--
-- Contas criadas (senha das duas: Medico12345):
--   medico@medcore.local        (DOCTOR)
--   recepcao@medcore.local      (RECEPTIONIST)

BEGIN;

INSERT INTO clinics (id, name, legal_name, email, phone, address, city, country, timezone,
                     default_appointment_duration, status, created_at, updated_at)
VALUES ('11111111-1111-1111-1111-111111111111', 'MedCore Clínica Central', 'MedCore Saúde, Lda.',
        'geral@medcore.local', '+351210000000', 'Avenida da Liberdade, 100', 'Lisboa', 'PT',
        'Europe/Lisbon', 30, 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO clinic_opening_hours (clinic_id, day_of_week, open_time, close_time)
SELECT '11111111-1111-1111-1111-111111111111', d, '08:00', '19:00'
FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']) AS d
ON CONFLICT DO NOTHING;

-- Senha: Medico12345 (BCrypt)
INSERT INTO users (id, email, password_hash, role, status, email_verified, two_factor_enabled, created_at, updated_at)
VALUES ('22222222-2222-2222-2222-222222222222', 'medico@medcore.local',
        '{bcrypt}$2y$10$Z3d00R9H42PqLG4Z3pAJkemtCU9OtS69X1wufPOaV8o0EyPQ4gpyi',
        'DOCTOR', 'ACTIVE', TRUE, FALSE, now(), now()),
       ('44444444-4444-4444-4444-444444444444', 'recepcao@medcore.local',
        '{bcrypt}$2y$10$Z3d00R9H42PqLG4Z3pAJkemtCU9OtS69X1wufPOaV8o0EyPQ4gpyi',
        'RECEPTIONIST', 'ACTIVE', TRUE, FALSE, now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO doctors (id, user_id, clinic_id, first_name, last_name, license_number, biography,
                     consultation_price, status, created_at, updated_at)
VALUES ('33333333-3333-3333-3333-333333333333', '22222222-2222-2222-2222-222222222222',
        '11111111-1111-1111-1111-111111111111', 'Rita', 'Costa', 'OM-12345',
        'Médica de clínica geral com 10 anos de experiência.', 60.00, 'ACTIVE', now(), now())
ON CONFLICT (id) DO NOTHING;

INSERT INTO doctor_specialties (doctor_id, specialty_id)
VALUES ('33333333-3333-3333-3333-333333333333', '0b8e3c43-0a51-4d43-9c1f-6f0f3f0d0001') -- General Practice
ON CONFLICT DO NOTHING;

INSERT INTO clinic_staff (id, clinic_id, user_id, created_at)
VALUES ('55555555-5555-5555-5555-555555555555', '11111111-1111-1111-1111-111111111111',
        '44444444-4444-4444-4444-444444444444', now())
ON CONFLICT DO NOTHING;

-- Horário do médico: segunda a sábado, 09:00-17:00, consultas de 30 minutos
INSERT INTO schedules (id, doctor_id, day_of_week, start_time, end_time, appointment_duration, active)
SELECT gen_random_uuid(), '33333333-3333-3333-3333-333333333333', d, '09:00', '17:00', 30, TRUE
FROM unnest(ARRAY['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY']) AS d
WHERE NOT EXISTS (SELECT 1 FROM schedules WHERE doctor_id = '33333333-3333-3333-3333-333333333333');

COMMIT;
