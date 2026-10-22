-- =====================================================================
-- V14: admins add accounts in Karung (audit action USER_CREATE; docs/specs/admin-spec.md)
-- =====================================================================

-- V12's CHECK on admin_audit_log.action has a generated name; find it by its definition.
DO $$
DECLARE c RECORD;
BEGIN
    FOR c IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = 'admin_audit_log'::regclass AND contype = 'c'
          AND pg_get_constraintdef(oid) LIKE '%CREDITS_ADJUST%'
    LOOP
        EXECUTE format('ALTER TABLE admin_audit_log DROP CONSTRAINT %I', c.conname);
    END LOOP;
END $$;

ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action CHECK (action IN (
    'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT', 'USER_CREATE'
));
