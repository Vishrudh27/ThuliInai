-- Update the initial admin user password hash in case the row already existed with a placeholder hash
UPDATE users
SET password_hash = '$2y$10$QSkGDUFAXIzpOVgMVgVLNuVoQE.DgdwZlfqTaULC2vmce5yhbWeR.'
WHERE email = 'admin@thuliinai.test';
