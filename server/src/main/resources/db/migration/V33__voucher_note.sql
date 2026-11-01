-- Vouchers made in Karung: what a promo code is for, and the two new audited actions.
ALTER TABLE credit_vouchers ADD COLUMN note TEXT;

ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_action;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_action CHECK (action IN (
    'CREDITS_ADJUST', 'USER_SIGN_OUT_ALL', 'USER_DISABLE', 'USER_ENABLE', 'PASSWORD_RESET_SEND', 'USERS_EXPORT', 'USER_CREATE',
    'SETTINGS_UPDATE', 'CONTENT_PUBLISH', 'VOUCHER_CREATE', 'VOUCHER_REVOKE'
));

ALTER TABLE admin_audit_log DROP CONSTRAINT admin_audit_log_target_type_check;
ALTER TABLE admin_audit_log ADD CONSTRAINT admin_audit_log_target_type_check
    CHECK (target_type IN ('USER', 'SETTING', 'CONTENT', 'EXPORT', 'VOUCHER'));
