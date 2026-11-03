-- Built-in packing templates are edited in Karung (owner, 3 Nov 2026): four audit actions and their target type.
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action CHECK (action IN (
    'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT', 'USER_CREATE',
    'SETTINGS_UPDATE', 'CONTENT_PUBLISH', 'VOUCHER_CREATE', 'VOUCHER_REVOKE',
    'TEMPLATE_CREATE', 'TEMPLATE_UPDATE', 'TEMPLATE_DELETE', 'TEMPLATE_RESTORE'
));
ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_target_type_check;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_target_type_check CHECK (target_type IN (
    'USER', 'SETTING', 'CONTENT', 'EXPORT', 'VOUCHER', 'TEMPLATE'
));
