-- system: 1.0.0-beta fresh-install schema. Not an upgrade path.
-- Apply through the migration runner; never edit after this baseline is released.
SET search_path = public;
--
--


SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: system_authz_version; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_authz_version (
    id integer NOT NULL,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT system_authz_version_id_check CHECK ((id = 1))
);


--
-- Name: system_depts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_depts (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    leader_user_id bigint,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_depts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_depts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_depts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_depts_id_seq OWNED BY public.system_depts.id;


--
-- Name: system_dict_data; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_data (
    id bigint NOT NULL,
    dict_type character varying(128) NOT NULL,
    label character varying(128) NOT NULL,
    value character varying(128) NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_dict_data_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_data_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_data_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_dict_data_id_seq OWNED BY public.system_dict_data.id;


--
-- Name: system_dict_types; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_dict_types (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    type character varying(128) NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_dict_types_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_dict_types_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_dict_types_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_dict_types_id_seq OWNED BY public.system_dict_types.id;


--
-- Name: system_login_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_login_logs (
    id bigint NOT NULL,
    user_id bigint,
    username character varying(64),
    user_ip character varying(64),
    user_agent character varying(512),
    login_result smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_login_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_login_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_login_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_login_logs_id_seq OWNED BY public.system_login_logs.id;


--
-- Name: system_menus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_menus (
    id bigint NOT NULL,
    name character varying(64) NOT NULL,
    permission character varying(128),
    type smallint DEFAULT 0 NOT NULL,
    parent_id bigint DEFAULT 0 NOT NULL,
    path character varying(255),
    component character varying(255),
    icon character varying(128),
    sort integer DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    visible boolean DEFAULT true NOT NULL
);


--
-- Name: system_menus_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_menus_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_menus_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_menus_id_seq OWNED BY public.system_menus.id;


--
-- Name: system_message_channels; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_message_channels (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    code character varying(64) NOT NULL,
    type character varying(32) NOT NULL,
    config text,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    deleted smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_message_channels_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_message_channels_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_message_channels_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_message_channels_id_seq OWNED BY public.system_message_channels.id;


--
-- Name: system_message_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_message_logs (
    id bigint NOT NULL,
    channel_id bigint NOT NULL,
    template_id bigint,
    template_code character varying(64),
    receiver character varying(255) NOT NULL,
    content text,
    params text,
    send_status smallint DEFAULT 0 NOT NULL,
    send_time bigint,
    error_message text,
    user_id bigint,
    user_type smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_message_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_message_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_message_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_message_logs_id_seq OWNED BY public.system_message_logs.id;


--
-- Name: system_message_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_message_templates (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    code character varying(64) NOT NULL,
    content text NOT NULL,
    params text,
    channel_id bigint DEFAULT 0 NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    deleted smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_message_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_message_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_message_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_message_templates_id_seq OWNED BY public.system_message_templates.id;


--
-- Name: system_mfa; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mfa (
    user_id bigint NOT NULL,
    required integer DEFAULT 0 NOT NULL,
    secret text,
    revision bigint DEFAULT 0 NOT NULL,
    last_step bigint DEFAULT '-1'::integer NOT NULL,
    CONSTRAINT system_mfa_required_check CHECK ((required = ANY (ARRAY[0, 1])))
);


--
-- Name: system_mfa_challenges; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_mfa_challenges (
    user_id bigint NOT NULL,
    token_hash character varying(64) NOT NULL,
    revision bigint NOT NULL,
    enrollment integer NOT NULL,
    secret text,
    password_hash text NOT NULL,
    expires_at bigint NOT NULL,
    attempts integer DEFAULT 0 NOT NULL
);


--
-- Name: system_notices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notices (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    content text NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_notices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_notices_id_seq OWNED BY public.system_notices.id;


--
-- Name: system_notification_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notification_templates (
    id bigint NOT NULL,
    name character varying(128) NOT NULL,
    code character varying(64) NOT NULL,
    type smallint DEFAULT 0 NOT NULL,
    message_template_id bigint DEFAULT 0 NOT NULL,
    params text,
    status smallint DEFAULT 0 NOT NULL,
    remark character varying(512),
    deleted smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_notification_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notification_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notification_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_notification_templates_id_seq OWNED BY public.system_notification_templates.id;


--
-- Name: system_notify_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_notify_messages (
    id bigint NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    template_id bigint DEFAULT 0 NOT NULL,
    template_code character varying(64),
    template_type smallint DEFAULT 0 NOT NULL,
    template_nickname character varying(64),
    template_content text,
    template_params text,
    read_status smallint DEFAULT 0 NOT NULL,
    read_time bigint,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_notify_messages_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_notify_messages_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notify_messages_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_notify_messages_id_seq OWNED BY public.system_notify_messages.id;


--
-- Name: system_operate_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_operate_logs (
    id bigint NOT NULL,
    user_id bigint,
    module character varying(64) NOT NULL,
    name character varying(64) NOT NULL,
    operate_type smallint DEFAULT 0 NOT NULL,
    request_method character varying(16),
    request_url character varying(512),
    request_params text,
    response_result text,
    user_ip character varying(64),
    duration integer DEFAULT 0 NOT NULL,
    result_code integer DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_operate_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_operate_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_operate_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_operate_logs_id_seq OWNED BY public.system_operate_logs.id;


--
-- Name: system_posts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_posts (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(64) NOT NULL,
    sort integer DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_posts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_posts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_posts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_posts_id_seq OWNED BY public.system_posts.id;


--
-- Name: system_role_menus; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_role_menus (
    id bigint NOT NULL,
    role_id bigint NOT NULL,
    menu_id bigint NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_role_menus_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_role_menus_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_role_menus_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_role_menus_id_seq OWNED BY public.system_role_menus.id;


--
-- Name: system_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_roles (
    id bigint NOT NULL,
    code character varying(64) NOT NULL,
    name character varying(64) NOT NULL,
    description character varying(512),
    sort integer DEFAULT 0 NOT NULL,
    status smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_roles_id_seq OWNED BY public.system_roles.id;


--
-- Name: system_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_settings (
    id bigint NOT NULL,
    category character varying(64) NOT NULL,
    setting_key character varying(128) NOT NULL,
    value text NOT NULL,
    value_type smallint DEFAULT 0 NOT NULL,
    name character varying(128) NOT NULL,
    remark text,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_settings_id_seq OWNED BY public.system_settings.id;


--
-- Name: system_social_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_social_users (
    id bigint NOT NULL,
    user_id bigint DEFAULT 0 NOT NULL,
    user_type smallint DEFAULT 0 NOT NULL,
    social_type character varying(32) NOT NULL,
    open_id character varying(128) NOT NULL,
    token character varying(512),
    raw_token_info text,
    nickname character varying(64),
    avatar character varying(512),
    raw_user_info text,
    deleted smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_social_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_social_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_social_users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_social_users_id_seq OWNED BY public.system_social_users.id;


--
-- Name: system_user_roles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_roles (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    role_id bigint NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_user_roles_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_user_roles_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_user_roles_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_user_roles_id_seq OWNED BY public.system_user_roles.id;


--
-- Name: system_user_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_user_sessions (
    user_id bigint NOT NULL,
    session_version bigint DEFAULT 0 NOT NULL
);


--
-- Name: system_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.system_users (
    id bigint NOT NULL,
    username character varying(64) NOT NULL,
    password_hash character varying(255) NOT NULL,
    nickname character varying(64) NOT NULL,
    email character varying(128),
    mobile character varying(32),
    avatar character varying(512),
    status smallint DEFAULT 0 NOT NULL,
    deleted smallint DEFAULT 0 NOT NULL,
    created_at bigint DEFAULT 0 NOT NULL,
    updated_at bigint DEFAULT 0 NOT NULL,
    gender smallint DEFAULT 0 NOT NULL,
    dept_id bigint DEFAULT 0 NOT NULL,
    remark character varying(512)
);


--
-- Name: system_users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.system_users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_users_id_seq OWNED BY public.system_users.id;


--
-- Name: system_depts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_depts ALTER COLUMN id SET DEFAULT nextval('public.system_depts_id_seq'::regclass);


--
-- Name: system_dict_data id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_data ALTER COLUMN id SET DEFAULT nextval('public.system_dict_data_id_seq'::regclass);


--
-- Name: system_dict_types id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_types ALTER COLUMN id SET DEFAULT nextval('public.system_dict_types_id_seq'::regclass);


--
-- Name: system_login_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_login_logs ALTER COLUMN id SET DEFAULT nextval('public.system_login_logs_id_seq'::regclass);


--
-- Name: system_menus id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_menus ALTER COLUMN id SET DEFAULT nextval('public.system_menus_id_seq'::regclass);


--
-- Name: system_message_channels id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_channels ALTER COLUMN id SET DEFAULT nextval('public.system_message_channels_id_seq'::regclass);


--
-- Name: system_message_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_logs ALTER COLUMN id SET DEFAULT nextval('public.system_message_logs_id_seq'::regclass);


--
-- Name: system_message_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_templates ALTER COLUMN id SET DEFAULT nextval('public.system_message_templates_id_seq'::regclass);


--
-- Name: system_notices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notices ALTER COLUMN id SET DEFAULT nextval('public.system_notices_id_seq'::regclass);


--
-- Name: system_notification_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notification_templates ALTER COLUMN id SET DEFAULT nextval('public.system_notification_templates_id_seq'::regclass);


--
-- Name: system_notify_messages id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_messages ALTER COLUMN id SET DEFAULT nextval('public.system_notify_messages_id_seq'::regclass);


--
-- Name: system_operate_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_operate_logs ALTER COLUMN id SET DEFAULT nextval('public.system_operate_logs_id_seq'::regclass);


--
-- Name: system_posts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_posts ALTER COLUMN id SET DEFAULT nextval('public.system_posts_id_seq'::regclass);


--
-- Name: system_role_menus id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role_menus ALTER COLUMN id SET DEFAULT nextval('public.system_role_menus_id_seq'::regclass);


--
-- Name: system_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_roles ALTER COLUMN id SET DEFAULT nextval('public.system_roles_id_seq'::regclass);


--
-- Name: system_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings ALTER COLUMN id SET DEFAULT nextval('public.system_settings_id_seq'::regclass);


--
-- Name: system_social_users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_users ALTER COLUMN id SET DEFAULT nextval('public.system_social_users_id_seq'::regclass);


--
-- Name: system_user_roles id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_roles ALTER COLUMN id SET DEFAULT nextval('public.system_user_roles_id_seq'::regclass);


--
-- Name: system_users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_users ALTER COLUMN id SET DEFAULT nextval('public.system_users_id_seq'::regclass);


--
-- Name: system_authz_version system_authz_version_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_authz_version
    ADD CONSTRAINT system_authz_version_pkey PRIMARY KEY (id);


--
-- Name: system_depts system_depts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_depts
    ADD CONSTRAINT system_depts_pkey PRIMARY KEY (id);


--
-- Name: system_dict_data system_dict_data_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_data
    ADD CONSTRAINT system_dict_data_pkey PRIMARY KEY (id);


--
-- Name: system_dict_types system_dict_types_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_dict_types
    ADD CONSTRAINT system_dict_types_pkey PRIMARY KEY (id);


--
-- Name: system_login_logs system_login_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_login_logs
    ADD CONSTRAINT system_login_logs_pkey PRIMARY KEY (id);


--
-- Name: system_menus system_menus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_menus
    ADD CONSTRAINT system_menus_pkey PRIMARY KEY (id);


--
-- Name: system_message_channels system_message_channels_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_channels
    ADD CONSTRAINT system_message_channels_pkey PRIMARY KEY (id);


--
-- Name: system_message_logs system_message_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_logs
    ADD CONSTRAINT system_message_logs_pkey PRIMARY KEY (id);


--
-- Name: system_message_templates system_message_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_message_templates
    ADD CONSTRAINT system_message_templates_pkey PRIMARY KEY (id);


--
-- Name: system_mfa_challenges system_mfa_challenges_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mfa_challenges
    ADD CONSTRAINT system_mfa_challenges_pkey PRIMARY KEY (user_id);


--
-- Name: system_mfa_challenges system_mfa_challenges_token_hash_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mfa_challenges
    ADD CONSTRAINT system_mfa_challenges_token_hash_key UNIQUE (token_hash);


--
-- Name: system_mfa system_mfa_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_mfa
    ADD CONSTRAINT system_mfa_pkey PRIMARY KEY (user_id);


--
-- Name: system_notices system_notices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notices
    ADD CONSTRAINT system_notices_pkey PRIMARY KEY (id);


--
-- Name: system_notification_templates system_notification_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notification_templates
    ADD CONSTRAINT system_notification_templates_pkey PRIMARY KEY (id);


--
-- Name: system_notify_messages system_notify_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notify_messages
    ADD CONSTRAINT system_notify_messages_pkey PRIMARY KEY (id);


--
-- Name: system_operate_logs system_operate_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_operate_logs
    ADD CONSTRAINT system_operate_logs_pkey PRIMARY KEY (id);


--
-- Name: system_posts system_posts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_posts
    ADD CONSTRAINT system_posts_pkey PRIMARY KEY (id);


--
-- Name: system_role_menus system_role_menus_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_role_menus
    ADD CONSTRAINT system_role_menus_pkey PRIMARY KEY (id);


--
-- Name: system_roles system_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_roles
    ADD CONSTRAINT system_roles_pkey PRIMARY KEY (id);


--
-- Name: system_settings system_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_settings
    ADD CONSTRAINT system_settings_pkey PRIMARY KEY (id);


--
-- Name: system_social_users system_social_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_social_users
    ADD CONSTRAINT system_social_users_pkey PRIMARY KEY (id);


--
-- Name: system_user_roles system_user_roles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_roles
    ADD CONSTRAINT system_user_roles_pkey PRIMARY KEY (id);


--
-- Name: system_user_sessions system_user_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_user_sessions
    ADD CONSTRAINT system_user_sessions_pkey PRIMARY KEY (user_id);


--
-- Name: system_users system_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_users
    ADD CONSTRAINT system_users_pkey PRIMARY KEY (id);


--
-- Name: idx_system_dict_data_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_dict_data_type ON public.system_dict_data USING btree (dict_type);


--
-- Name: idx_system_dict_types_type; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_dict_types_type ON public.system_dict_types USING btree (type);


--
-- Name: idx_system_login_logs_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_login_logs_user ON public.system_login_logs USING btree (user_id);


--
-- Name: idx_system_message_channels_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_message_channels_code ON public.system_message_channels USING btree (code);


--
-- Name: idx_system_message_logs_channel; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_message_logs_channel ON public.system_message_logs USING btree (channel_id);


--
-- Name: idx_system_message_logs_receiver; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_message_logs_receiver ON public.system_message_logs USING btree (receiver);


--
-- Name: idx_system_message_templates_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_message_templates_code ON public.system_message_templates USING btree (code);


--
-- Name: idx_system_notification_templates_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_notification_templates_code ON public.system_notification_templates USING btree (code);


--
-- Name: idx_system_notify_messages_read; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_notify_messages_read ON public.system_notify_messages USING btree (user_id, read_status);


--
-- Name: idx_system_notify_messages_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_notify_messages_user ON public.system_notify_messages USING btree (user_id, user_type);


--
-- Name: idx_system_operate_logs_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_operate_logs_user ON public.system_operate_logs USING btree (user_id);


--
-- Name: idx_system_role_menus_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_role_menus_role ON public.system_role_menus USING btree (role_id);


--
-- Name: idx_system_roles_code; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_roles_code ON public.system_roles USING btree (code);


--
-- Name: idx_system_settings_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_settings_category ON public.system_settings USING btree (category);


--
-- Name: idx_system_social_users_social; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_social_users_social ON public.system_social_users USING btree (social_type, open_id);


--
-- Name: idx_system_social_users_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_social_users_user ON public.system_social_users USING btree (user_id, user_type);


--
-- Name: idx_system_user_roles_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_roles_role ON public.system_user_roles USING btree (role_id);


--
-- Name: idx_system_user_roles_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_user_roles_user ON public.system_user_roles USING btree (user_id);


--
-- Name: idx_system_users_username; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX idx_system_users_username ON public.system_users USING btree (username);


--
-- Name: uk_system_settings_key; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uk_system_settings_key ON public.system_settings USING btree (setting_key);


--
--


SET search_path = public;
