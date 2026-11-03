-- RANSEL schema, squashed on 3 Nov 2026 before the first store release (owner): the result of the former
-- migrations V1-V34, made with pg_dump --schema-only from a database that ran them, minus the unused
-- content_strings table (screen texts stopped being remote content). Every database before this date was reset.
-- From here on: never edit this file once it is applied in production; add V2, V3, ...

CREATE FUNCTION public.set_updated_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TABLE public.admin_audit_log (
    id uuid NOT NULL,
    admin_id uuid NOT NULL,
    admin_email text NOT NULL,
    action text NOT NULL,
    target_type text NOT NULL,
    target_id text,
    before jsonb,
    after jsonb,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT admin_audit_log_action CHECK ((action = ANY (ARRAY['CREDITS_ADJUST'::text, 'USER_SIGN_OUT_ALL'::text, 'USER_DISABLE'::text, 'USER_ENABLE'::text, 'PASSWORD_RESET_SEND'::text, 'USERS_EXPORT'::text, 'USER_CREATE'::text, 'SETTINGS_UPDATE'::text, 'CONTENT_PUBLISH'::text, 'VOUCHER_CREATE'::text, 'VOUCHER_REVOKE'::text]))),
    CONSTRAINT admin_audit_log_target_type_check CHECK ((target_type = ANY (ARRAY['USER'::text, 'SETTING'::text, 'CONTENT'::text, 'EXPORT'::text, 'VOUCHER'::text])))
);

CREATE TABLE public.ai_search_log (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    trip_id uuid,
    kind text NOT NULL,
    destination text NOT NULL,
    price_level text,
    interests text[] DEFAULT '{}'::text[] NOT NULL,
    new_search boolean NOT NULL,
    source text NOT NULL,
    cards integer DEFAULT 0 NOT NULL,
    place_ids text[] DEFAULT '{}'::text[] NOT NULL,
    latency_ms integer NOT NULL,
    credits_charged integer DEFAULT 0 NOT NULL,
    llm_cost_micro_usd bigint DEFAULT 0 NOT NULL,
    places_cost_micro_usd bigint DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT ai_search_log_kind_check CHECK ((kind = ANY (ARRAY['EXPLORE'::text, 'POLL'::text]))),
    CONSTRAINT ai_search_log_source_check CHECK ((source = ANY (ARRAY['NEW'::text, 'CACHE'::text, 'FAILED'::text, 'NO_CREDITS'::text])))
);

CREATE TABLE public.app_settings (
    key text NOT NULL,
    value text NOT NULL,
    updated_by uuid NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT app_settings_key_check CHECK ((key = ANY (ARRAY['SIGNUP_GRANT'::text, 'DAILY_FREE'::text, 'COST_SEARCH'::text, 'COST_NEXT_BATCH'::text, 'COST_PLACE_DETAIL'::text, 'COST_RECEIPT'::text, 'AI_MONTHLY_BUDGET_USD'::text, 'AI_BUDGET_ALERT_PERCENT'::text, 'AI_BUDGET_MODE'::text, 'SUGGESTIONS_PER_HOUR'::text, 'LLM_MODEL'::text, 'ANDROID_MIN_VERSION'::text, 'ANDROID_FORCE_UPDATE'::text, 'ANDROID_STORE_URL'::text, 'IOS_MIN_VERSION'::text, 'IOS_FORCE_UPDATE'::text, 'IOS_STORE_URL'::text])))
);

CREATE TABLE public.comments (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    target_kind text NOT NULL,
    target_id uuid NOT NULL,
    author_member_id uuid NOT NULL,
    body text NOT NULL,
    mentions jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    edited_at timestamp with time zone,
    deleted_at timestamp with time zone,
    CONSTRAINT comments_body_check CHECK (((length(body) >= 1) AND (length(body) <= 1000))),
    CONSTRAINT comments_target_kind_check CHECK ((target_kind = ANY (ARRAY['ITINERARY_ITEM'::text, 'EXPENSE'::text, 'POLL_OPTION'::text])))
);

CREATE TABLE public.content_categories (
    kind text NOT NULL,
    constant text NOT NULL,
    label_id text,
    label_en text,
    icon text,
    color text,
    sort_order integer NOT NULL,
    hidden boolean DEFAULT false NOT NULL,
    updated_by uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT content_categories_kind_check CHECK ((kind = ANY (ARRAY['ITINERARY'::text, 'EXPENSE'::text, 'PACKING'::text])))
);

CREATE TABLE public.content_errors (
    code text NOT NULL,
    status text NOT NULL,
    title_id text,
    title_en text,
    message_id text,
    message_en text,
    button_id text,
    button_en text,
    illustration text,
    display text,
    updated_by uuid,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT content_errors_display_check CHECK ((display = ANY (ARRAY['FULL_SCREEN'::text, 'SNACKBAR'::text, 'DIALOG'::text]))),
    CONSTRAINT content_errors_status_check CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text])))
);

CREATE TABLE public.content_meta (
    id smallint NOT NULL,
    version integer DEFAULT 0 NOT NULL,
    published_at timestamp with time zone,
    published_by uuid,
    CONSTRAINT content_meta_id_check CHECK ((id = 1))
);

CREATE TABLE public.credit_daily (
    user_id uuid NOT NULL,
    usage_date date NOT NULL,
    free_used integer DEFAULT 0 NOT NULL,
    CONSTRAINT credit_daily_free_used_check CHECK ((free_used >= 0))
);

CREATE TABLE public.credit_ledger (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    kind text NOT NULL,
    delta integer NOT NULL,
    source text,
    action text,
    ref text,
    idempotency_key text,
    balance_after integer NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    note text,
    CONSTRAINT credit_ledger_action_check CHECK ((action = ANY (ARRAY['SEARCH'::text, 'NEXT_BATCH'::text, 'PLACE_DETAIL'::text, 'RECEIPT'::text]))),
    CONSTRAINT credit_ledger_delta_check CHECK ((delta <> 0)),
    CONSTRAINT credit_ledger_kind_check CHECK ((kind = ANY (ARRAY['SIGNUP_GRANT'::text, 'SPEND'::text, 'VOUCHER'::text, 'TOPUP'::text, 'REFUND'::text, 'ADJUSTMENT'::text]))),
    CONSTRAINT credit_ledger_kind_sign CHECK ((((kind = 'SPEND'::text) AND (delta < 0)) OR (kind = 'ADJUSTMENT'::text) OR ((kind <> ALL (ARRAY['SPEND'::text, 'ADJUSTMENT'::text])) AND (delta > 0)))),
    CONSTRAINT credit_ledger_source_check CHECK ((source = ANY (ARRAY['FREE_DAILY'::text, 'WALLET'::text])))
);

CREATE TABLE public.credit_voucher_redemptions (
    code text NOT NULL,
    user_id uuid NOT NULL,
    redeemed_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.credit_vouchers (
    code text NOT NULL,
    credits integer NOT NULL,
    max_uses integer DEFAULT 1 NOT NULL,
    used_count integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    note text,
    CONSTRAINT credit_vouchers_credits_check CHECK ((credits > 0)),
    CONSTRAINT credit_vouchers_max_uses_check CHECK ((max_uses > 0)),
    CONSTRAINT credit_vouchers_used_count_check CHECK ((used_count >= 0))
);

CREATE TABLE public.credit_wallets (
    user_id uuid NOT NULL,
    balance integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT credit_wallets_balance_check CHECK ((balance >= 0))
);

CREATE TABLE public.destination_interests (
    destination text NOT NULL,
    month smallint NOT NULL,
    language text NOT NULL,
    chips jsonb NOT NULL,
    curated_by uuid,
    curated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT destination_interests_language_check CHECK ((language = ANY (ARRAY['id'::text, 'en'::text]))),
    CONSTRAINT destination_interests_month_check CHECK (((month >= 1) AND (month <= 12)))
);

CREATE TABLE public.devices (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    platform text NOT NULL,
    push_token text NOT NULL,
    locale text DEFAULT 'id'::text NOT NULL,
    app_version text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone DEFAULT now() NOT NULL,
    disabled_at timestamp with time zone,
    CONSTRAINT devices_platform_check CHECK ((platform = ANY (ARRAY['ANDROID'::text, 'IOS'::text])))
);

CREATE TABLE public.email_verification_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.error_count_users (
    hour timestamp with time zone NOT NULL,
    code text NOT NULL,
    route text NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE public.error_counts (
    hour timestamp with time zone NOT NULL,
    code text NOT NULL,
    route text NOT NULL,
    status integer NOT NULL,
    count bigint NOT NULL,
    last_at timestamp with time zone NOT NULL
);

CREATE TABLE public.expense_items (
    expense_id uuid NOT NULL,
    sort_order integer NOT NULL,
    name text NOT NULL,
    amount_minor bigint NOT NULL,
    member_ids jsonb DEFAULT '[]'::jsonb NOT NULL,
    CONSTRAINT expense_items_amount_minor_check CHECK ((amount_minor >= 0)),
    CONSTRAINT expense_items_name_check CHECK (((length(name) >= 1) AND (length(name) <= 120)))
);

CREATE TABLE public.expense_payers (
    expense_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    member_id uuid NOT NULL,
    CONSTRAINT expense_payers_amount_minor_check CHECK ((amount_minor > 0))
);

CREATE TABLE public.expense_splits (
    expense_id uuid NOT NULL,
    share_minor bigint NOT NULL,
    member_id uuid NOT NULL,
    CONSTRAINT expense_splits_share_minor_check CHECK ((share_minor >= 0))
);

CREATE TABLE public.expenses (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    currency character(3) NOT NULL,
    fx_rate_to_base numeric(20,10),
    category text DEFAULT 'OTHER'::text NOT NULL,
    payment_method text DEFAULT 'CASH'::text NOT NULL,
    note text,
    spent_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    paid_by_member_id uuid NOT NULL,
    is_estimate boolean DEFAULT false NOT NULL,
    fx_source text,
    fx_date date,
    items_split boolean DEFAULT true NOT NULL,
    CONSTRAINT expenses_amount_minor_check CHECK ((amount_minor > 0)),
    CONSTRAINT expenses_category_check CHECK ((category = ANY (ARRAY['ATTRACTION'::text, 'FOOD'::text, 'TRANSPORT'::text, 'LODGING'::text, 'SHOPPING'::text, 'OTHER'::text]))),
    CONSTRAINT expenses_currency_check CHECK ((currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT expenses_fx_rate_to_base_check CHECK (((fx_rate_to_base IS NULL) OR (fx_rate_to_base > (0)::numeric))),
    CONSTRAINT expenses_fx_source_check CHECK ((fx_source = ANY (ARRAY['DAILY'::text, 'MANUAL'::text]))),
    CONSTRAINT expenses_payment_method_check CHECK ((payment_method = ANY (ARRAY['CASH'::text, 'DEBIT_CARD'::text, 'CREDIT_CARD'::text, 'TRANSFER'::text, 'E_WALLET'::text, 'OTHER'::text])))
);

CREATE TABLE public.explore_suggestions_cache (
    destination text NOT NULL,
    month character(7) NOT NULL,
    language text NOT NULL,
    interests jsonb NOT NULL,
    price_low_max_usd numeric(10,2) NOT NULL,
    price_mid_max_usd numeric(10,2) NOT NULL,
    model text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT explore_suggestions_cache_month_check CHECK ((month ~ '^\d{4}-(0[1-9]|1[0-2])$'::text))
);

CREATE TABLE public.fx_rates (
    rate_date date NOT NULL,
    currency character(3) NOT NULL,
    per_euro numeric(20,8) NOT NULL,
    CONSTRAINT fx_rates_per_euro_check CHECK ((per_euro > (0)::numeric))
);

CREATE TABLE public.guest_sessions (
    token_hash text NOT NULL,
    trip_id uuid NOT NULL,
    member_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    last_seen_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.itinerary_items (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    day_date date,
    start_time time without time zone,
    end_time time without time zone,
    title text NOT NULL,
    category text DEFAULT 'OTHER'::text NOT NULL,
    place_name text,
    place_id text,
    lat double precision,
    lng double precision,
    duration_min integer,
    notes text,
    booking_url text,
    source text DEFAULT 'MANUAL'::text NOT NULL,
    sort_order double precision DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT itinerary_items_category_check CHECK ((category = ANY (ARRAY['ATTRACTION'::text, 'FOOD'::text, 'TRANSPORT'::text, 'LODGING'::text, 'SHOPPING'::text, 'OTHER'::text]))),
    CONSTRAINT itinerary_items_duration_min_check CHECK (((duration_min IS NULL) OR (duration_min > 0))),
    CONSTRAINT itinerary_items_lat_check CHECK (((lat >= ('-90'::integer)::double precision) AND (lat <= (90)::double precision))),
    CONSTRAINT itinerary_items_lng_check CHECK (((lng >= ('-180'::integer)::double precision) AND (lng <= (180)::double precision))),
    CONSTRAINT itinerary_items_source_check CHECK ((source = ANY (ARRAY['MANUAL'::text, 'AI'::text]))),
    CONSTRAINT itinerary_items_title_check CHECK ((length(TRIM(BOTH FROM title)) > 0)),
    CONSTRAINT itinerary_latlng_pair CHECK (((lat IS NULL) = (lng IS NULL)))
);

CREATE TABLE public.link_previews (
    url_hash text NOT NULL,
    url text NOT NULL,
    final_url text NOT NULL,
    title text,
    image_url text,
    site_name text,
    address text,
    rating_value double precision,
    rating_count integer,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.member_claims (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    guest_member_id uuid NOT NULL,
    claimed_by_member_id uuid NOT NULL,
    moved jsonb DEFAULT '{}'::jsonb NOT NULL,
    claimed_at timestamp with time zone DEFAULT now() NOT NULL,
    undone_at timestamp with time zone
);

CREATE TABLE public.member_list_items (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    category text NOT NULL,
    name text NOT NULL,
    quantity integer NOT NULL,
    required boolean DEFAULT false NOT NULL,
    sort_order double precision DEFAULT 0 NOT NULL,
    CONSTRAINT member_list_items_name_check CHECK (((length(TRIM(BOTH FROM name)) >= 1) AND (length(TRIM(BOTH FROM name)) <= 100))),
    CONSTRAINT member_list_items_quantity_check CHECK (((quantity >= 1) AND (quantity <= 999)))
);

CREATE TABLE public.member_list_recipients (
    trip_id uuid NOT NULL,
    member_id uuid NOT NULL
);

CREATE TABLE public.member_lists (
    trip_id uuid NOT NULL,
    name text NOT NULL,
    version integer DEFAULT 1 NOT NULL,
    sent_to_all boolean DEFAULT false NOT NULL,
    sent_at timestamp with time zone,
    created_by_member_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT member_lists_name_check CHECK (((length(TRIM(BOTH FROM name)) >= 1) AND (length(TRIM(BOTH FROM name)) <= 60)))
);

CREATE TABLE public.notification_settings (
    user_id uuid NOT NULL,
    trip_activity boolean DEFAULT true NOT NULL,
    expenses boolean DEFAULT true NOT NULL,
    reminders boolean DEFAULT true NOT NULL
);

CREATE TABLE public.notifications (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    type text NOT NULL,
    trip_id uuid,
    params jsonb DEFAULT '{}'::jsonb NOT NULL,
    dedupe_key text NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    read_at timestamp with time zone,
    push_state text DEFAULT 'PENDING'::text NOT NULL,
    push_attempts smallint DEFAULT 0 NOT NULL,
    CONSTRAINT notifications_push_state_check CHECK ((push_state = ANY (ARRAY['PENDING'::text, 'SENT'::text, 'SKIPPED'::text]))),
    CONSTRAINT notifications_type_check CHECK ((type = ANY (ARRAY['MEMBER_JOINED'::text, 'MEMBER_REMOVED'::text, 'ROLE_CHANGED'::text, 'EXPENSE_ADDED'::text, 'SETTLEMENT_MARKED'::text, 'VOTE_REQUESTED'::text, 'VOTE_RESULT'::text, 'PACKING_REMINDER'::text, 'ACTIVITY_REMINDER'::text, 'MEMBER_CLAIMED'::text, 'COMMENT_MENTION'::text, 'POLL_REQUESTED'::text, 'POLL_RESULT'::text, 'PACKING_LIST'::text, 'DOCUMENT_ADDED'::text, 'RECAP_READY'::text])))
);

CREATE TABLE public.packing_items (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    category text DEFAULT 'OTHER'::text NOT NULL,
    name text NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    is_packed boolean DEFAULT false NOT NULL,
    sort_order double precision DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    assignee_member_id uuid,
    CONSTRAINT packing_items_category_check CHECK ((category = ANY (ARRAY['CLOTHING'::text, 'TOILETRIES'::text, 'ELECTRONICS'::text, 'DOCUMENTS'::text, 'HEALTH'::text, 'OTHER'::text]))),
    CONSTRAINT packing_items_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0)),
    CONSTRAINT packing_items_quantity_check CHECK ((quantity > 0))
);

CREATE TABLE public.packing_templates (
    id uuid NOT NULL,
    owner_id uuid,
    name text NOT NULL,
    items jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    shared_trip_id uuid,
    CONSTRAINT packing_templates_items_check CHECK ((jsonb_typeof(items) = 'array'::text)),
    CONSTRAINT packing_templates_name_check CHECK ((length(TRIM(BOTH FROM name)) > 0))
);

CREATE TABLE public.password_reset_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.payment_methods (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    kind text NOT NULL,
    provider text NOT NULL,
    account_number text NOT NULL,
    holder_name text NOT NULL,
    is_primary boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT payment_methods_account_number_check CHECK (((length(account_number) >= 4) AND (length(account_number) <= 40))),
    CONSTRAINT payment_methods_holder_name_check CHECK (((length(holder_name) >= 1) AND (length(holder_name) <= 80))),
    CONSTRAINT payment_methods_kind_check CHECK ((kind = ANY (ARRAY['BANK'::text, 'EWALLET'::text]))),
    CONSTRAINT payment_methods_provider_check CHECK (((length(provider) >= 1) AND (length(provider) <= 40)))
);

CREATE TABLE public.personal_items (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    owner_member_id uuid NOT NULL,
    category text NOT NULL,
    name text NOT NULL,
    quantity integer NOT NULL,
    is_packed boolean DEFAULT false NOT NULL,
    sort_order double precision DEFAULT 0 NOT NULL,
    source_item_id uuid,
    required boolean DEFAULT false NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    customized boolean DEFAULT false NOT NULL,
    CONSTRAINT personal_items_name_check CHECK (((length(TRIM(BOTH FROM name)) >= 1) AND (length(TRIM(BOTH FROM name)) <= 100))),
    CONSTRAINT personal_items_quantity_check CHECK ((quantity > 0))
);

CREATE TABLE public.place_autocomplete_cache (
    query_key text NOT NULL,
    suggestions jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.place_detail_cache (
    place_id text NOT NULL,
    lang text DEFAULT 'id'::text NOT NULL,
    response jsonb NOT NULL,
    model text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

CREATE TABLE public.place_details_cache (
    place_id text NOT NULL,
    place jsonb NOT NULL,
    fetched_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.poll_options (
    id uuid NOT NULL,
    poll_id uuid NOT NULL,
    label text NOT NULL,
    start_date date,
    end_date date,
    place_id text,
    url text,
    image_url text,
    price_minor bigint,
    price_unit text,
    min_minor bigint,
    max_minor bigint,
    version integer DEFAULT 1 NOT NULL,
    sort_order integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT poll_options_label_check CHECK (((length(TRIM(BOTH FROM label)) >= 1) AND (length(TRIM(BOTH FROM label)) <= 100))),
    CONSTRAINT poll_options_min_minor_check CHECK (((min_minor IS NULL) OR (min_minor >= 0))),
    CONSTRAINT poll_options_price_minor_check CHECK (((price_minor IS NULL) OR (price_minor >= 0))),
    CONSTRAINT poll_options_price_unit_check CHECK (((price_unit IS NULL) OR (price_unit = ANY (ARRAY['NIGHT'::text, 'PERSON'::text, 'TOTAL'::text]))))
);

CREATE TABLE public.poll_votes (
    option_id uuid NOT NULL,
    poll_id uuid NOT NULL,
    member_id uuid NOT NULL,
    option_version integer NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.polls (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    kind text NOT NULL,
    question text NOT NULL,
    created_by_member_id uuid NOT NULL,
    closes_at timestamp with time zone NOT NULL,
    multi_choice boolean DEFAULT false NOT NULL,
    is_anonymous boolean DEFAULT false NOT NULL,
    anonymity_locked boolean DEFAULT false NOT NULL,
    apply_winner boolean DEFAULT true NOT NULL,
    day_date date,
    start_time time without time zone,
    status text DEFAULT 'OPEN'::text NOT NULL,
    decided_option_id uuid,
    decided_at timestamp with time zone,
    tied_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    share_code text,
    CONSTRAINT polls_budget_is_anonymous CHECK (((kind <> 'BUDGET'::text) OR is_anonymous)),
    CONSTRAINT polls_kind_check CHECK ((kind = ANY (ARRAY['DATE'::text, 'PLACE'::text, 'STAY'::text, 'FOOD'::text, 'BUDGET'::text, 'DESTINATION'::text, 'TRANSPORT'::text, 'CUSTOM'::text]))),
    CONSTRAINT polls_question_check CHECK (((length(TRIM(BOTH FROM question)) >= 1) AND (length(TRIM(BOTH FROM question)) <= 140))),
    CONSTRAINT polls_status_check CHECK ((status = ANY (ARRAY['OPEN'::text, 'TIED'::text, 'CLOSED'::text, 'DECIDED'::text])))
);

CREATE TABLE public.provider_usage (
    day date NOT NULL,
    call text NOT NULL,
    service text NOT NULL,
    calls bigint NOT NULL,
    cost_micro_usd bigint NOT NULL,
    CONSTRAINT provider_usage_service_check CHECK ((service = ANY (ARRAY['LLM'::text, 'PLACES'::text])))
);

CREATE TABLE public.recommendation_cache (
    query_hash text NOT NULL,
    destination text NOT NULL,
    response jsonb NOT NULL,
    model text NOT NULL,
    hit_count integer DEFAULT 0 NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    expires_at timestamp with time zone NOT NULL
);

CREATE TABLE public.recommendation_feedback (
    trip_id uuid NOT NULL,
    user_id uuid NOT NULL,
    place_id text NOT NULL,
    place_name text NOT NULL,
    category text NOT NULL,
    action text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT recommendation_feedback_action_check CHECK ((action = ANY (ARRAY['LIKE'::text, 'SKIP'::text]))),
    CONSTRAINT recommendation_feedback_category_check CHECK ((category = ANY (ARRAY['ATTRACTION'::text, 'FOOD'::text, 'TRANSPORT'::text, 'LODGING'::text, 'SHOPPING'::text, 'OTHER'::text])))
);

CREATE TABLE public.refresh_tokens (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    token_hash text NOT NULL,
    device_name text,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL
);

CREATE TABLE public.settlements (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    amount_minor bigint NOT NULL,
    settled_at timestamp with time zone DEFAULT now() NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    from_member_id uuid NOT NULL,
    to_member_id uuid NOT NULL,
    status text DEFAULT 'CONFIRMED'::text NOT NULL,
    claimed_at timestamp with time zone,
    CONSTRAINT settlements_amount_minor_check CHECK ((amount_minor > 0)),
    CONSTRAINT settlements_different_members CHECK ((from_member_id <> to_member_id)),
    CONSTRAINT settlements_status_check CHECK ((status = ANY (ARRAY['CLAIMED_PAID'::text, 'CONFIRMED'::text])))
);

CREATE TABLE public.suggestion_round_places (
    round_id uuid NOT NULL,
    place_id text NOT NULL,
    category text NOT NULL,
    sort_order integer NOT NULL,
    snapshot jsonb NOT NULL,
    chosen boolean DEFAULT false NOT NULL,
    CONSTRAINT suggestion_round_places_category_check CHECK ((category = ANY (ARRAY['ATTRACTION'::text, 'FOOD'::text, 'TRANSPORT'::text, 'LODGING'::text, 'SHOPPING'::text, 'OTHER'::text])))
);

CREATE TABLE public.suggestion_rounds (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    created_by uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    closes_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    decided_at timestamp with time zone,
    decided_by uuid,
    CONSTRAINT suggestion_rounds_check CHECK (((closes_at > created_at) AND (expires_at >= closes_at)))
);

CREATE TABLE public.suggestion_votes (
    round_id uuid NOT NULL,
    place_id text NOT NULL,
    user_id uuid NOT NULL,
    vote text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT suggestion_votes_vote_check CHECK ((vote = ANY (ARRAY['LIKE'::text, 'SKIP'::text])))
);

CREATE TABLE public.trip_albums (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    url text NOT NULL,
    provider text NOT NULL,
    drive_folder_id text,
    resource_key text,
    title text,
    added_by_member_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT trip_albums_check CHECK (((provider = 'DRIVE_FOLDER'::text) = (drive_folder_id IS NOT NULL))),
    CONSTRAINT trip_albums_provider_check CHECK ((provider = ANY (ARRAY['DRIVE_FOLDER'::text, 'GOOGLE_PHOTOS'::text, 'OTHER'::text]))),
    CONSTRAINT trip_albums_title_check CHECK (((title IS NULL) OR ((length(TRIM(BOTH FROM title)) >= 1) AND (length(TRIM(BOTH FROM title)) <= 80)))),
    CONSTRAINT trip_albums_url_check CHECK (((length(url) >= 1) AND (length(url) <= 2000)))
);

CREATE TABLE public.trip_documents (
    id uuid NOT NULL,
    trip_id uuid NOT NULL,
    title text NOT NULL,
    category text NOT NULL,
    kind text NOT NULL,
    url text,
    file_name text,
    mime text,
    size_bytes bigint,
    itinerary_item_id uuid,
    uploaded_by_member_id uuid NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    file_removed_at timestamp with time zone,
    CONSTRAINT trip_documents_category_check CHECK ((category = ANY (ARRAY['TRAVEL'::text, 'STAY'::text, 'ACTIVITY'::text, 'OTHER'::text]))),
    CONSTRAINT trip_documents_check CHECK ((((kind = 'LINK'::text) AND (url IS NOT NULL)) OR ((kind = 'FILE'::text) AND (size_bytes IS NOT NULL) AND (mime IS NOT NULL)))),
    CONSTRAINT trip_documents_kind_check CHECK ((kind = ANY (ARRAY['FILE'::text, 'LINK'::text]))),
    CONSTRAINT trip_documents_title_check CHECK (((length(TRIM(BOTH FROM title)) >= 1) AND (length(TRIM(BOTH FROM title)) <= 80)))
);

CREATE TABLE public.trip_invites (
    code text NOT NULL,
    trip_id uuid NOT NULL,
    role text DEFAULT 'EDITOR'::text NOT NULL,
    created_by uuid NOT NULL,
    max_uses integer,
    used_count integer DEFAULT 0 NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT invites_usage_limit CHECK (((max_uses IS NULL) OR (used_count <= max_uses))),
    CONSTRAINT trip_invites_code_check CHECK (((length(code) >= 6) AND (length(code) <= 32))),
    CONSTRAINT trip_invites_max_uses_check CHECK (((max_uses IS NULL) OR (max_uses > 0))),
    CONSTRAINT trip_invites_role_check CHECK ((role = ANY (ARRAY['EDITOR'::text, 'VIEWER'::text]))),
    CONSTRAINT trip_invites_used_count_check CHECK ((used_count >= 0))
);

CREATE TABLE public.trip_members (
    trip_id uuid NOT NULL,
    user_id uuid,
    role text NOT NULL,
    joined_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    id uuid NOT NULL,
    display_name text,
    phone text,
    claimed_at timestamp with time zone,
    claimed_by_member_id uuid,
    removed_at timestamp with time zone,
    CONSTRAINT trip_members_guest_is_viewer CHECK (((user_id IS NOT NULL) OR (role = 'VIEWER'::text))),
    CONSTRAINT trip_members_has_name CHECK (((user_id IS NOT NULL) OR (display_name IS NOT NULL))),
    CONSTRAINT trip_members_role_check CHECK ((role = ANY (ARRAY['OWNER'::text, 'EDITOR'::text, 'VIEWER'::text])))
);

CREATE TABLE public.trip_templates (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    name text NOT NULL,
    day_count integer NOT NULL,
    destination text DEFAULT ''::text NOT NULL,
    items jsonb DEFAULT '[]'::jsonb NOT NULL,
    packing jsonb DEFAULT '[]'::jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT trip_templates_day_count_check CHECK ((day_count >= 0)),
    CONSTRAINT trip_templates_name_check CHECK (((length(TRIM(BOTH FROM name)) >= 1) AND (length(TRIM(BOTH FROM name)) <= 60)))
);

CREATE TABLE public.trips (
    id uuid NOT NULL,
    owner_id uuid NOT NULL,
    title text NOT NULL,
    destination text NOT NULL,
    start_date date,
    end_date date,
    base_currency character(3) DEFAULT 'IDR'::bpchar NOT NULL,
    budget_minor bigint,
    cover_url text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    deleted_at timestamp with time zone,
    dest_place_id text,
    dest_lat double precision,
    dest_lng double precision,
    dest_viewport jsonb,
    country_code character(2),
    local_currency character(3),
    time_zone text,
    CONSTRAINT trips_base_currency_check CHECK ((base_currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT trips_budget_minor_check CHECK (((budget_minor IS NULL) OR (budget_minor >= 0))),
    CONSTRAINT trips_date_range CHECK ((((start_date IS NULL) AND (end_date IS NULL)) OR ((start_date IS NOT NULL) AND (end_date IS NOT NULL) AND (end_date >= start_date)))),
    CONSTRAINT trips_dest_lat_check CHECK (((dest_lat >= ('-90'::integer)::double precision) AND (dest_lat <= (90)::double precision))),
    CONSTRAINT trips_dest_lng_check CHECK (((dest_lng >= ('-180'::integer)::double precision) AND (dest_lng <= (180)::double precision))),
    CONSTRAINT trips_time_zone_check CHECK (((time_zone IS NULL) OR ((length(time_zone) >= 1) AND (length(time_zone) <= 64)))),
    CONSTRAINT trips_title_check CHECK ((length(TRIM(BOTH FROM title)) > 0))
);

CREATE TABLE public.user_active_days (
    day date NOT NULL,
    user_id uuid NOT NULL
);

CREATE TABLE public.users (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    display_name text NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    email text,
    password_hash text,
    default_currency character(3) DEFAULT 'IDR'::bpchar NOT NULL,
    deleted_at timestamp with time zone,
    disabled_at timestamp with time zone,
    email_verified_at timestamp with time zone,
    last_seen_at timestamp with time zone,
    avatar_url text,
    CONSTRAINT users_default_currency_check CHECK ((default_currency ~ '^[A-Z]{3}$'::text)),
    CONSTRAINT users_email_needs_password CHECK (((email IS NULL) = (password_hash IS NULL)))
);

ALTER TABLE ONLY public.admin_audit_log
    ADD CONSTRAINT admin_audit_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.ai_search_log
    ADD CONSTRAINT ai_search_log_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT app_settings_pkey PRIMARY KEY (key);

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.content_categories
    ADD CONSTRAINT content_categories_pkey PRIMARY KEY (kind, constant);

ALTER TABLE ONLY public.content_errors
    ADD CONSTRAINT content_errors_pkey PRIMARY KEY (code, status);

ALTER TABLE ONLY public.content_meta
    ADD CONSTRAINT content_meta_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.credit_daily
    ADD CONSTRAINT credit_daily_pkey PRIMARY KEY (user_id, usage_date);

ALTER TABLE ONLY public.credit_ledger
    ADD CONSTRAINT credit_ledger_idempotency_key_key UNIQUE (idempotency_key);

ALTER TABLE ONLY public.credit_ledger
    ADD CONSTRAINT credit_ledger_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.credit_voucher_redemptions
    ADD CONSTRAINT credit_voucher_redemptions_pkey PRIMARY KEY (code, user_id);

ALTER TABLE ONLY public.credit_vouchers
    ADD CONSTRAINT credit_vouchers_pkey PRIMARY KEY (code);

ALTER TABLE ONLY public.credit_wallets
    ADD CONSTRAINT credit_wallets_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY public.destination_interests
    ADD CONSTRAINT destination_interests_pkey PRIMARY KEY (destination, month, language);

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_push_token_key UNIQUE (push_token);

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY public.error_count_users
    ADD CONSTRAINT error_count_users_pkey PRIMARY KEY (hour, code, route, user_id);

ALTER TABLE ONLY public.error_counts
    ADD CONSTRAINT error_counts_pkey PRIMARY KEY (hour, code, route);

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_pkey PRIMARY KEY (expense_id, sort_order);

ALTER TABLE ONLY public.expense_payers
    ADD CONSTRAINT expense_payers_pkey PRIMARY KEY (expense_id, member_id);

ALTER TABLE ONLY public.expense_splits
    ADD CONSTRAINT expense_splits_pkey PRIMARY KEY (expense_id, member_id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.explore_suggestions_cache
    ADD CONSTRAINT explore_suggestions_cache_pkey PRIMARY KEY (destination, month, language);

ALTER TABLE ONLY public.fx_rates
    ADD CONSTRAINT fx_rates_pkey PRIMARY KEY (rate_date, currency);

ALTER TABLE ONLY public.guest_sessions
    ADD CONSTRAINT guest_sessions_pkey PRIMARY KEY (token_hash);

ALTER TABLE ONLY public.itinerary_items
    ADD CONSTRAINT itinerary_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.link_previews
    ADD CONSTRAINT link_previews_pkey PRIMARY KEY (url_hash);

ALTER TABLE ONLY public.member_claims
    ADD CONSTRAINT member_claims_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.member_list_items
    ADD CONSTRAINT member_list_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.member_list_recipients
    ADD CONSTRAINT member_list_recipients_pkey PRIMARY KEY (trip_id, member_id);

ALTER TABLE ONLY public.member_lists
    ADD CONSTRAINT member_lists_pkey PRIMARY KEY (trip_id);

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT notification_settings_pkey PRIMARY KEY (user_id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_dedupe_key_key UNIQUE (user_id, dedupe_key);

ALTER TABLE ONLY public.packing_items
    ADD CONSTRAINT packing_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.packing_templates
    ADD CONSTRAINT packing_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.personal_items
    ADD CONSTRAINT personal_items_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.place_autocomplete_cache
    ADD CONSTRAINT place_autocomplete_cache_pkey PRIMARY KEY (query_key);

ALTER TABLE ONLY public.place_detail_cache
    ADD CONSTRAINT place_detail_cache_pkey PRIMARY KEY (place_id, lang);

ALTER TABLE ONLY public.place_details_cache
    ADD CONSTRAINT place_details_cache_pkey PRIMARY KEY (place_id);

ALTER TABLE ONLY public.poll_options
    ADD CONSTRAINT poll_options_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.poll_votes
    ADD CONSTRAINT poll_votes_pkey PRIMARY KEY (option_id, member_id);

ALTER TABLE ONLY public.polls
    ADD CONSTRAINT polls_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.provider_usage
    ADD CONSTRAINT provider_usage_pkey PRIMARY KEY (day, call);

ALTER TABLE ONLY public.recommendation_cache
    ADD CONSTRAINT recommendation_cache_pkey PRIMARY KEY (query_hash);

ALTER TABLE ONLY public.recommendation_feedback
    ADD CONSTRAINT recommendation_feedback_pkey PRIMARY KEY (trip_id, user_id, place_id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_hash_key UNIQUE (token_hash);

ALTER TABLE ONLY public.settlements
    ADD CONSTRAINT settlements_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suggestion_round_places
    ADD CONSTRAINT suggestion_round_places_pkey PRIMARY KEY (round_id, place_id);

ALTER TABLE ONLY public.suggestion_rounds
    ADD CONSTRAINT suggestion_rounds_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.suggestion_votes
    ADD CONSTRAINT suggestion_votes_pkey PRIMARY KEY (round_id, place_id, user_id);

ALTER TABLE ONLY public.trip_albums
    ADD CONSTRAINT trip_albums_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_documents
    ADD CONSTRAINT trip_documents_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT trip_invites_pkey PRIMARY KEY (code);

ALTER TABLE ONLY public.trip_members
    ADD CONSTRAINT trip_members_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trip_templates
    ADD CONSTRAINT trip_templates_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT trips_pkey PRIMARY KEY (id);

ALTER TABLE ONLY public.user_active_days
    ADD CONSTRAINT user_active_days_pkey PRIMARY KEY (day, user_id);

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);

CREATE UNIQUE INDEX guest_sessions_member ON public.guest_sessions USING btree (member_id);

CREATE INDEX idx_admin_audit_created ON public.admin_audit_log USING btree (created_at DESC);

CREATE INDEX idx_admin_audit_target ON public.admin_audit_log USING btree (target_id, created_at DESC);

CREATE INDEX idx_ai_search_log_created ON public.ai_search_log USING btree (created_at DESC);

CREATE INDEX idx_ai_search_log_user ON public.ai_search_log USING btree (user_id, created_at DESC);

CREATE INDEX idx_comments_target ON public.comments USING btree (trip_id, target_kind, target_id, created_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_credit_ledger_user ON public.credit_ledger USING btree (user_id, created_at DESC);

CREATE INDEX idx_devices_user ON public.devices USING btree (user_id) WHERE (disabled_at IS NULL);

CREATE INDEX idx_email_verification_user ON public.email_verification_tokens USING btree (user_id) WHERE (used_at IS NULL);

CREATE INDEX idx_error_counts_code ON public.error_counts USING btree (code, hour DESC);

CREATE INDEX idx_expense_payers_member ON public.expense_payers USING btree (member_id);

CREATE INDEX idx_expense_splits_member ON public.expense_splits USING btree (member_id);

CREATE INDEX idx_expenses_trip_spent ON public.expenses USING btree (trip_id, spent_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_expenses_trip_updated ON public.expenses USING btree (trip_id, updated_at);

CREATE INDEX idx_explore_suggestions_expires ON public.explore_suggestions_cache USING btree (expires_at);

CREATE INDEX idx_itinerary_trip_day ON public.itinerary_items USING btree (trip_id, day_date, sort_order) WHERE (deleted_at IS NULL);

CREATE INDEX idx_itinerary_trip_updated ON public.itinerary_items USING btree (trip_id, updated_at);

CREATE INDEX idx_link_previews_fetched ON public.link_previews USING btree (fetched_at);

CREATE INDEX idx_member_list_items_trip ON public.member_list_items USING btree (trip_id);

CREATE INDEX idx_notifications_pending ON public.notifications USING btree (created_at) WHERE (push_state = 'PENDING'::text);

CREATE INDEX idx_notifications_user ON public.notifications USING btree (user_id, created_at DESC);

CREATE INDEX idx_packing_trip ON public.packing_items USING btree (trip_id, category, sort_order) WHERE (deleted_at IS NULL);

CREATE INDEX idx_packing_trip_updated ON public.packing_items USING btree (trip_id, updated_at);

CREATE INDEX idx_password_reset_user ON public.password_reset_tokens USING btree (user_id) WHERE (used_at IS NULL);

CREATE INDEX idx_payment_methods_user ON public.payment_methods USING btree (user_id) WHERE (deleted_at IS NULL);

CREATE UNIQUE INDEX idx_personal_items_one_copy ON public.personal_items USING btree (owner_member_id, source_item_id) WHERE ((source_item_id IS NOT NULL) AND (deleted_at IS NULL));

CREATE INDEX idx_personal_items_owner ON public.personal_items USING btree (owner_member_id, updated_at);

CREATE INDEX idx_personal_items_source ON public.personal_items USING btree (source_item_id) WHERE (source_item_id IS NOT NULL);

CREATE INDEX idx_place_detail_cache_expires ON public.place_detail_cache USING btree (expires_at);

CREATE INDEX idx_poll_options_poll ON public.poll_options USING btree (poll_id, sort_order) WHERE (deleted_at IS NULL);

CREATE INDEX idx_poll_votes_poll ON public.poll_votes USING btree (poll_id, member_id);

CREATE INDEX idx_polls_due ON public.polls USING btree (closes_at) WHERE ((status = 'OPEN'::text) AND (deleted_at IS NULL));

CREATE INDEX idx_polls_tied ON public.polls USING btree (tied_at) WHERE ((status = 'TIED'::text) AND (deleted_at IS NULL));

CREATE INDEX idx_polls_trip ON public.polls USING btree (trip_id, created_at DESC) WHERE (deleted_at IS NULL);

CREATE INDEX idx_recommendation_cache_expires ON public.recommendation_cache USING btree (expires_at);

CREATE INDEX idx_refresh_tokens_user_active ON public.refresh_tokens USING btree (user_id) WHERE (revoked_at IS NULL);

CREATE INDEX idx_settlements_trip ON public.settlements USING btree (trip_id, settled_at) WHERE (deleted_at IS NULL);

CREATE INDEX idx_settlements_trip_updated ON public.settlements USING btree (trip_id, updated_at);

CREATE INDEX idx_suggestion_rounds_closing ON public.suggestion_rounds USING btree (closes_at) WHERE (decided_at IS NULL);

CREATE INDEX idx_suggestion_rounds_trip ON public.suggestion_rounds USING btree (trip_id, created_at DESC);

CREATE INDEX idx_suggestion_votes_user ON public.suggestion_votes USING btree (round_id, user_id);

CREATE INDEX idx_templates_owner ON public.packing_templates USING btree (owner_id, updated_at);

CREATE INDEX idx_templates_shared_trip ON public.packing_templates USING btree (shared_trip_id) WHERE (shared_trip_id IS NOT NULL);

CREATE INDEX idx_trip_invites_trip ON public.trip_invites USING btree (trip_id);

CREATE INDEX idx_trip_members_trip ON public.trip_members USING btree (trip_id) WHERE (removed_at IS NULL);

CREATE INDEX idx_trip_members_user ON public.trip_members USING btree (user_id);

CREATE INDEX idx_trips_dest_place ON public.trips USING btree (dest_place_id, created_at) WHERE ((dest_place_id IS NOT NULL) AND (deleted_at IS NULL));

CREATE INDEX idx_trips_owner_active ON public.trips USING btree (owner_id, start_date DESC) WHERE (deleted_at IS NULL);

CREATE INDEX idx_trips_owner_updated ON public.trips USING btree (owner_id, updated_at);

CREATE INDEX idx_users_last_seen ON public.users USING btree (last_seen_at);

CREATE UNIQUE INDEX polls_share_code ON public.polls USING btree (share_code) WHERE (share_code IS NOT NULL);

CREATE INDEX trip_albums_trip ON public.trip_albums USING btree (trip_id) WHERE (deleted_at IS NULL);

CREATE INDEX trip_documents_trip ON public.trip_documents USING btree (trip_id) WHERE (deleted_at IS NULL);

CREATE INDEX trip_templates_owner ON public.trip_templates USING btree (owner_id);

CREATE UNIQUE INDEX uq_itinerary_trip_place_ai ON public.itinerary_items USING btree (trip_id, place_id) WHERE ((source = 'AI'::text) AND (place_id IS NOT NULL) AND (deleted_at IS NULL));

CREATE UNIQUE INDEX uq_member_claims_claimer ON public.member_claims USING btree (trip_id, claimed_by_member_id) WHERE (undone_at IS NULL);

CREATE UNIQUE INDEX uq_member_claims_guest ON public.member_claims USING btree (guest_member_id) WHERE (undone_at IS NULL);

CREATE UNIQUE INDEX uq_trip_members_user ON public.trip_members USING btree (trip_id, user_id) WHERE (user_id IS NOT NULL);

CREATE UNIQUE INDEX uq_trip_single_owner ON public.trip_members USING btree (trip_id) WHERE (role = 'OWNER'::text);

CREATE UNIQUE INDEX uq_users_email_lower ON public.users USING btree (lower(email)) WHERE (email IS NOT NULL);

CREATE TRIGGER trg_credit_wallets_updated_at BEFORE UPDATE ON public.credit_wallets FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_expenses_updated_at BEFORE UPDATE ON public.expenses FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_itinerary_updated_at BEFORE UPDATE ON public.itinerary_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_member_lists_updated_at BEFORE UPDATE ON public.member_lists FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_packing_updated_at BEFORE UPDATE ON public.packing_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_payment_methods_updated_at BEFORE UPDATE ON public.payment_methods FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_personal_items_updated_at BEFORE UPDATE ON public.personal_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_polls_updated_at BEFORE UPDATE ON public.polls FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_settlements_updated_at BEFORE UPDATE ON public.settlements FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_templates_updated_at BEFORE UPDATE ON public.packing_templates FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_trip_albums_updated_at BEFORE UPDATE ON public.trip_albums FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_trip_documents_updated_at BEFORE UPDATE ON public.trip_documents FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_trip_members_updated_at BEFORE UPDATE ON public.trip_members FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_trips_updated_at BEFORE UPDATE ON public.trips FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON public.users FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT app_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_author_member_id_fkey FOREIGN KEY (author_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.comments
    ADD CONSTRAINT comments_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.content_categories
    ADD CONSTRAINT content_categories_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.content_errors
    ADD CONSTRAINT content_errors_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.content_meta
    ADD CONSTRAINT content_meta_published_by_fkey FOREIGN KEY (published_by) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.credit_daily
    ADD CONSTRAINT credit_daily_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.credit_ledger
    ADD CONSTRAINT credit_ledger_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.credit_voucher_redemptions
    ADD CONSTRAINT credit_voucher_redemptions_code_fkey FOREIGN KEY (code) REFERENCES public.credit_vouchers(code);

ALTER TABLE ONLY public.credit_voucher_redemptions
    ADD CONSTRAINT credit_voucher_redemptions_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.credit_wallets
    ADD CONSTRAINT credit_wallets_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.destination_interests
    ADD CONSTRAINT destination_interests_curated_by_fkey FOREIGN KEY (curated_by) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.devices
    ADD CONSTRAINT devices_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.email_verification_tokens
    ADD CONSTRAINT email_verification_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.expense_items
    ADD CONSTRAINT expense_items_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.expenses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.expense_payers
    ADD CONSTRAINT expense_payers_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.expenses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.expense_payers
    ADD CONSTRAINT expense_payers_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.expense_splits
    ADD CONSTRAINT expense_splits_expense_id_fkey FOREIGN KEY (expense_id) REFERENCES public.expenses(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.expense_splits
    ADD CONSTRAINT expense_splits_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_paid_by_member_id_fkey FOREIGN KEY (paid_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.expenses
    ADD CONSTRAINT expenses_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.guest_sessions
    ADD CONSTRAINT guest_sessions_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.trip_members(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.guest_sessions
    ADD CONSTRAINT guest_sessions_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.itinerary_items
    ADD CONSTRAINT itinerary_items_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.member_claims
    ADD CONSTRAINT member_claims_claimed_by_member_id_fkey FOREIGN KEY (claimed_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.member_claims
    ADD CONSTRAINT member_claims_guest_member_id_fkey FOREIGN KEY (guest_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.member_claims
    ADD CONSTRAINT member_claims_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.member_list_items
    ADD CONSTRAINT member_list_items_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.member_lists(trip_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.member_list_recipients
    ADD CONSTRAINT member_list_recipients_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.member_list_recipients
    ADD CONSTRAINT member_list_recipients_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.member_lists(trip_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.member_lists
    ADD CONSTRAINT member_lists_created_by_member_id_fkey FOREIGN KEY (created_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.member_lists
    ADD CONSTRAINT member_lists_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.notification_settings
    ADD CONSTRAINT notification_settings_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.notifications
    ADD CONSTRAINT notifications_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.packing_items
    ADD CONSTRAINT packing_items_assignee_member_id_fkey FOREIGN KEY (assignee_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.packing_items
    ADD CONSTRAINT packing_items_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.packing_templates
    ADD CONSTRAINT packing_templates_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);

ALTER TABLE ONLY public.packing_templates
    ADD CONSTRAINT packing_templates_shared_trip_id_fkey FOREIGN KEY (shared_trip_id) REFERENCES public.trips(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.password_reset_tokens
    ADD CONSTRAINT password_reset_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.payment_methods
    ADD CONSTRAINT payment_methods_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.personal_items
    ADD CONSTRAINT personal_items_owner_member_id_fkey FOREIGN KEY (owner_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.personal_items
    ADD CONSTRAINT personal_items_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.poll_options
    ADD CONSTRAINT poll_options_poll_id_fkey FOREIGN KEY (poll_id) REFERENCES public.polls(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.poll_votes
    ADD CONSTRAINT poll_votes_member_id_fkey FOREIGN KEY (member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.poll_votes
    ADD CONSTRAINT poll_votes_option_id_fkey FOREIGN KEY (option_id) REFERENCES public.poll_options(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.poll_votes
    ADD CONSTRAINT poll_votes_poll_id_fkey FOREIGN KEY (poll_id) REFERENCES public.polls(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.polls
    ADD CONSTRAINT polls_created_by_member_id_fkey FOREIGN KEY (created_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.polls
    ADD CONSTRAINT polls_decided_option_id_fkey FOREIGN KEY (decided_option_id) REFERENCES public.poll_options(id);

ALTER TABLE ONLY public.polls
    ADD CONSTRAINT polls_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.recommendation_feedback
    ADD CONSTRAINT recommendation_feedback_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.recommendation_feedback
    ADD CONSTRAINT recommendation_feedback_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.settlements
    ADD CONSTRAINT settlements_from_member_id_fkey FOREIGN KEY (from_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.settlements
    ADD CONSTRAINT settlements_to_member_id_fkey FOREIGN KEY (to_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.settlements
    ADD CONSTRAINT settlements_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suggestion_round_places
    ADD CONSTRAINT suggestion_round_places_round_id_fkey FOREIGN KEY (round_id) REFERENCES public.suggestion_rounds(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suggestion_rounds
    ADD CONSTRAINT suggestion_rounds_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suggestion_rounds
    ADD CONSTRAINT suggestion_rounds_decided_by_fkey FOREIGN KEY (decided_by) REFERENCES public.users(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.suggestion_rounds
    ADD CONSTRAINT suggestion_rounds_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suggestion_votes
    ADD CONSTRAINT suggestion_votes_round_id_place_id_fkey FOREIGN KEY (round_id, place_id) REFERENCES public.suggestion_round_places(round_id, place_id) ON DELETE CASCADE;

ALTER TABLE ONLY public.suggestion_votes
    ADD CONSTRAINT suggestion_votes_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_albums
    ADD CONSTRAINT trip_albums_added_by_member_id_fkey FOREIGN KEY (added_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.trip_albums
    ADD CONSTRAINT trip_albums_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_documents
    ADD CONSTRAINT trip_documents_itinerary_item_id_fkey FOREIGN KEY (itinerary_item_id) REFERENCES public.itinerary_items(id) ON DELETE SET NULL;

ALTER TABLE ONLY public.trip_documents
    ADD CONSTRAINT trip_documents_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_documents
    ADD CONSTRAINT trip_documents_uploaded_by_member_id_fkey FOREIGN KEY (uploaded_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT trip_invites_created_by_fkey FOREIGN KEY (created_by) REFERENCES public.users(id);

ALTER TABLE ONLY public.trip_invites
    ADD CONSTRAINT trip_invites_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_members
    ADD CONSTRAINT trip_members_claimed_by_member_id_fkey FOREIGN KEY (claimed_by_member_id) REFERENCES public.trip_members(id);

ALTER TABLE ONLY public.trip_members
    ADD CONSTRAINT trip_members_trip_id_fkey FOREIGN KEY (trip_id) REFERENCES public.trips(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_members
    ADD CONSTRAINT trip_members_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trip_templates
    ADD CONSTRAINT trip_templates_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id) ON DELETE CASCADE;

ALTER TABLE ONLY public.trips
    ADD CONSTRAINT trips_owner_id_fkey FOREIGN KEY (owner_id) REFERENCES public.users(id);

-- The dummy owner of guest data before the first sign-in (server/CLAUDE.md); it has no email and cannot log in.
INSERT INTO public.users (id, display_name) VALUES ('00000000-0000-0000-0000-000000000001', 'Local User');

-- Remote content version (Karung stage D).
INSERT INTO public.content_meta (id, version) VALUES (1, 0);
