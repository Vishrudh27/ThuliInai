-- Seed the initial super-admin account.
-- Password: Admin@ThuliInai2026!
-- Hash generated with: htpasswd -nbBC 10 admin "Admin@ThuliInai2026!" | cut -d: -f2
-- To change the password in production: UPDATE users SET password_hash = '<new_bcrypt_hash>'
--   WHERE email = 'admin@thuliinai.test';
-- BCrypt hashes are safe to store in source control — they cannot be reversed.
INSERT INTO users (email, password_hash, role, created_at)
VALUES (
    'admin@thuliinai.test',
    '$2y$10$QSkGDUFAXIzpOVgMVgVLNuVoQE.DgdwZlfqTaULC2vmce5yhbWeR.',
    'ADMIN',
    now()
)
ON CONFLICT (email) DO NOTHING;
