-- Firebase Authentication: link AppUser rows to Firebase Auth UIDs.
-- password_hash remains for legacy session logins and bootstrap admin; Firebase-only
-- users get a random unusable BCrypt placeholder at provision time.

ALTER TABLE users ADD COLUMN IF NOT EXISTS firebase_uid VARCHAR(128);
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_firebase_uid ON users(firebase_uid)
    WHERE firebase_uid IS NOT NULL;
