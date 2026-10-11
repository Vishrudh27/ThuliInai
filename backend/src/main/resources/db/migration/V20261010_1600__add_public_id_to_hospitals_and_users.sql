-- Add non-sequential public UUIDs to prevent enumeration attacks
ALTER TABLE hospitals
  ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_hospitals_public_id ON hospitals (public_id);

ALTER TABLE users
  ADD COLUMN public_id UUID NOT NULL DEFAULT gen_random_uuid();

CREATE UNIQUE INDEX uq_users_public_id ON users (public_id);
