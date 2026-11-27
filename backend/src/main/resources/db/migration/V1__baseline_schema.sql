-- ============================================================
-- V1: the original EduSync schema (44 tables), as it was before any migration.
--
-- Made from backend/database/EduSync_DB.sql with
--   pg_restore --schema-only --no-owner --no-privileges --no-comments
-- then cleaned for Flyway: removed the psql-only \restrict lines, the session
-- SET lines and the search_path reset (it would break the next migrations).
--
-- Databases that already have these tables skip this file
-- (spring.flyway.baseline-on-migrate=true, baseline-version=1).
-- An EMPTY database runs it first, then V2, V3...
--
-- Never edit this file. Every later change goes into a new V<n>__*.sql file.
-- ============================================================

-- Name: pgcrypto; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;


--
-- Name: vector; Type: EXTENSION; Schema: -; Owner: -
--

CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;




--
-- Name: academic_program; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_program (
    program_id bigint NOT NULL,
    code character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    active boolean,
    department character varying(150)
);


--
-- Name: academic_program_program_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.academic_program ALTER COLUMN program_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.academic_program_program_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: academic_term; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.academic_term (
    term_id bigint NOT NULL,
    academic_year character varying(20) NOT NULL,
    term_type character varying(20) NOT NULL,
    start_date date,
    end_date date,
    CONSTRAINT chk_academic_term_dates CHECK (((start_date IS NULL) OR (end_date IS NULL) OR (end_date >= start_date)))
);


--
-- Name: academic_term_term_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.academic_term ALTER COLUMN term_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.academic_term_term_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ai_citation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_citation (
    citation_id bigint NOT NULL,
    message_id bigint NOT NULL,
    chunk_id bigint NOT NULL,
    rank integer
);


--
-- Name: ai_citation_citation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ai_citation ALTER COLUMN citation_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ai_citation_citation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ai_conversation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_conversation (
    conversation_id bigint NOT NULL,
    user_id bigint NOT NULL,
    title character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    offering_id bigint
);


--
-- Name: ai_conversation_conversation_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ai_conversation ALTER COLUMN conversation_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ai_conversation_conversation_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ai_message; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ai_message (
    message_id bigint NOT NULL,
    conversation_id bigint NOT NULL,
    sender character varying(20) NOT NULL,
    content text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    model character varying(100),
    token_count integer,
    CONSTRAINT chk_message_sender CHECK (((sender)::text = ANY ((ARRAY['USER'::character varying, 'ASSISTANT'::character varying])::text[])))
);


--
-- Name: ai_message_message_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ai_message ALTER COLUMN message_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ai_message_message_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment (
    due_at timestamp without time zone NOT NULL,
    grade_item_id bigint NOT NULL,
    instructions text,
    opens_at timestamp without time zone
);


--
-- Name: assignment_attachment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignment_attachment (
    grade_item_id bigint NOT NULL,
    file_asset_id bigint NOT NULL
);


--
-- Name: attendance_change; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_change (
    change_id bigint NOT NULL,
    record_id bigint NOT NULL,
    old_status character varying(20) NOT NULL,
    new_status character varying(20) NOT NULL,
    reason_code character varying(50),
    doctor_id bigint NOT NULL,
    changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: attendance_change_change_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attendance_change ALTER COLUMN change_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attendance_change_change_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: attendance_record; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_record (
    record_id bigint NOT NULL,
    session_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    status character varying(20) NOT NULL,
    method character varying(30) NOT NULL,
    marked_at timestamp without time zone,
    CONSTRAINT attendance_record_method_check CHECK (((method)::text = ANY ((ARRAY['LOCAL_QR'::character varying, 'INTERNET_QR'::character varying, 'MANUAL'::character varying])::text[]))),
    CONSTRAINT chk_attendance_record_status CHECK (((status)::text = ANY ((ARRAY['PRESENT'::character varying, 'LATE'::character varying, 'ABSENT'::character varying])::text[])))
);


--
-- Name: attendance_record_record_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attendance_record ALTER COLUMN record_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attendance_record_record_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: attendance_session; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_session (
    session_id bigint NOT NULL,
    group_id bigint NOT NULL,
    room_id bigint,
    method character varying(30) NOT NULL,
    state character varying(20) NOT NULL,
    teaching_assignment_id bigint NOT NULL,
    starts_at timestamp without time zone,
    ends_at timestamp without time zone,
    qr_window_ends_at timestamp without time zone,
    late_after_minutes integer,
    CONSTRAINT attendance_session_method_check CHECK (((method)::text = ANY ((ARRAY['LOCAL_QR'::character varying, 'INTERNET_QR'::character varying, 'MANUAL'::character varying])::text[]))),
    CONSTRAINT chk_attendance_session_dates CHECK (((ends_at IS NULL) OR (starts_at IS NULL) OR (ends_at > starts_at)))
);


--
-- Name: attendance_session_session_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attendance_session ALTER COLUMN session_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attendance_session_session_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: attendance_warning; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_warning (
    warning_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    rule_id bigint NOT NULL,
    absence_count integer DEFAULT 0 NOT NULL,
    late_count integer DEFAULT 0 NOT NULL,
    issued_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    emailed_at timestamp without time zone
);


--
-- Name: attendance_warning_rule; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.attendance_warning_rule (
    rule_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    group_type character varying(30) NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    absence_limit integer NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: attendance_warning_rule_rule_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attendance_warning_rule ALTER COLUMN rule_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attendance_warning_rule_rule_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: attendance_warning_warning_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.attendance_warning ALTER COLUMN warning_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.attendance_warning_warning_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: audit_event; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.audit_event (
    event_id bigint NOT NULL,
    actor_id bigint NOT NULL,
    target_type character varying(100) NOT NULL,
    target_id bigint NOT NULL,
    action character varying(100) NOT NULL,
    occurred_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    before_json jsonb,
    after_json jsonb,
    ip inet
);


--
-- Name: audit_event_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.audit_event ALTER COLUMN event_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.audit_event_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: control_export; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.control_export (
    export_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    file_asset_id bigint NOT NULL,
    generated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: control_export_export_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.control_export ALTER COLUMN export_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.control_export_export_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: course; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course (
    course_id bigint NOT NULL,
    course_code character varying(30) NOT NULL,
    title character varying(200) NOT NULL,
    credit_hours integer NOT NULL
);


--
-- Name: course_course_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.course ALTER COLUMN course_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.course_course_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: course_material; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_material (
    material_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    file_asset_id bigint NOT NULL,
    lecture_no integer,
    title character varying(200) NOT NULL,
    approved_at timestamp without time zone,
    kind character varying(30),
    ai_enabled boolean,
    ai_status character varying(30),
    ai_error text,
    indexed_at timestamp without time zone
);


--
-- Name: course_material_material_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.course_material ALTER COLUMN material_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.course_material_material_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: course_offering; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.course_offering (
    offering_id bigint NOT NULL,
    course_id bigint NOT NULL,
    term_id bigint NOT NULL,
    coursework_max numeric(5,2),
    section character varying(30),
    capacity integer,
    CONSTRAINT chk_course_offering_capacity CHECK (((capacity IS NULL) OR (capacity > 0)))
);


--
-- Name: course_offering_offering_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.course_offering ALTER COLUMN offering_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.course_offering_offering_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: enrollment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.enrollment (
    enrollment_id bigint NOT NULL,
    student_user_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    program_id bigint NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    seat_no character varying(30)
);


--
-- Name: enrollment_enrollment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.enrollment ALTER COLUMN enrollment_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.enrollment_enrollment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: file_asset; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.file_asset (
    file_asset_id bigint NOT NULL,
    uploader_id bigint NOT NULL,
    storage_key character varying(500) NOT NULL,
    original_name character varying(255) NOT NULL,
    mime_type character varying(100) NOT NULL,
    size_bytes bigint,
    sha256 character varying(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp without time zone
);


--
-- Name: file_asset_file_asset_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.file_asset ALTER COLUMN file_asset_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.file_asset_file_asset_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_change; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_change (
    change_id bigint NOT NULL,
    student_grade_id bigint NOT NULL,
    old_score numeric(6,2) NOT NULL,
    new_score numeric(6,2) NOT NULL,
    batch_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    changed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    reason text,
    CONSTRAINT chk_grade_change_scores CHECK (((old_score >= (0)::numeric) AND (new_score >= (0)::numeric)))
);


--
-- Name: grade_change_change_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_change ALTER COLUMN change_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_change_change_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_email; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_email (
    grade_email_id bigint NOT NULL,
    send_batch_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    email_snapshot character varying(255) NOT NULL,
    content_snapshot text NOT NULL,
    state character varying(30) NOT NULL,
    sent_at timestamp without time zone,
    attempts integer DEFAULT 0 NOT NULL,
    error text,
    provider_message_id character varying(255)
);


--
-- Name: grade_email_grade_email_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_email ALTER COLUMN grade_email_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_email_grade_email_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_import_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_import_batch (
    batch_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    file_asset_id bigint NOT NULL,
    state character varying(30) NOT NULL,
    approved_at timestamp without time zone
);


--
-- Name: grade_import_batch_batch_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_import_batch ALTER COLUMN batch_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_import_batch_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_import_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_import_entry (
    entry_id bigint NOT NULL,
    sheet_id bigint NOT NULL,
    row_no integer NOT NULL,
    column_key character varying(100) NOT NULL,
    enrollment_id bigint,
    grade_item_id bigint NOT NULL,
    proposed_score numeric(6,2),
    raw_university_id character varying(50),
    raw_value text,
    status character varying(30),
    error_message text,
    CONSTRAINT chk_import_entry_row CHECK ((row_no > 0)),
    CONSTRAINT chk_import_entry_score CHECK ((proposed_score >= (0)::numeric))
);


--
-- Name: grade_import_entry_entry_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_import_entry ALTER COLUMN entry_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_import_entry_entry_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_import_sheet; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_import_sheet (
    sheet_id bigint NOT NULL,
    batch_id bigint NOT NULL,
    program_id bigint NOT NULL,
    sheet_name character varying(150) NOT NULL,
    validation_state character varying(30) NOT NULL
);


--
-- Name: grade_import_sheet_sheet_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_import_sheet ALTER COLUMN sheet_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_import_sheet_sheet_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_item (
    grade_item_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    assessment_type character varying(30) NOT NULL,
    title character varying(200) NOT NULL,
    max_score numeric(6,2) NOT NULL,
    scope character varying(30),
    CONSTRAINT chk_grade_item_score CHECK ((max_score >= (0)::numeric))
);


--
-- Name: grade_item_grade_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_item ALTER COLUMN grade_item_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_item_grade_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: grade_item_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_item_group (
    grade_item_id bigint NOT NULL,
    group_id bigint NOT NULL
);


--
-- Name: grade_send_batch; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.grade_send_batch (
    send_batch_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    reviewed_at timestamp without time zone,
    sent_at timestamp without time zone,
    CONSTRAINT chk_send_batch_dates CHECK (((sent_at IS NULL) OR ((reviewed_at IS NOT NULL) AND (sent_at >= reviewed_at))))
);


--
-- Name: grade_send_batch_send_batch_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.grade_send_batch ALTER COLUMN send_batch_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.grade_send_batch_send_batch_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: group_membership; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.group_membership (
    membership_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    group_id bigint NOT NULL,
    starts_at timestamp without time zone NOT NULL,
    ends_at timestamp without time zone NOT NULL,
    CONSTRAINT chk_membership_period CHECK (((ends_at IS NULL) OR (ends_at > starts_at)))
);


--
-- Name: group_membership_membership_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.group_membership ALTER COLUMN membership_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.group_membership_membership_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: knowledge_chunk; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.knowledge_chunk (
    chunk_id bigint NOT NULL,
    material_id bigint NOT NULL,
    page_no integer,
    chunk_no integer NOT NULL,
    content text NOT NULL,
    embedding public.vector(384),
    active boolean DEFAULT true NOT NULL,
    embedding_model character varying(100),
    content_hash character varying(64),
    CONSTRAINT chk_chunk_number CHECK ((chunk_no > 0)),
    CONSTRAINT chk_chunk_page CHECK (((page_no IS NULL) OR (page_no > 0)))
);


--
-- Name: knowledge_chunk_chunk_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.knowledge_chunk ALTER COLUMN chunk_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.knowledge_chunk_chunk_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: password_reset_token; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.password_reset_token (
    token_id bigint NOT NULL,
    user_id bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    used_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: password_reset_token_token_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.password_reset_token ALTER COLUMN token_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.password_reset_token_token_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: program_plan_course; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.program_plan_course (
    plan_course_id bigint NOT NULL,
    program_id bigint NOT NULL,
    course_id bigint NOT NULL,
    level integer NOT NULL,
    term_type character varying(30) NOT NULL
);


--
-- Name: program_plan_course_plan_course_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.program_plan_course ALTER COLUMN plan_course_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.program_plan_course_plan_course_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: qr_challenge; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.qr_challenge (
    challenge_id bigint NOT NULL,
    session_id bigint NOT NULL,
    token_hash character varying(255) NOT NULL,
    expires_at timestamp without time zone NOT NULL
);


--
-- Name: qr_challenge_challenge_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.qr_challenge ALTER COLUMN challenge_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.qr_challenge_challenge_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: registration_item; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_item (
    item_id bigint NOT NULL,
    request_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    status character varying(30) NOT NULL
);


--
-- Name: registration_item_item_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.registration_item ALTER COLUMN item_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.registration_item_item_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: registration_request; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.registration_request (
    request_id bigint NOT NULL,
    student_user_id bigint NOT NULL,
    term_id bigint NOT NULL,
    advisor_id bigint,
    admin_id bigint,
    status character varying(30) NOT NULL,
    total_credits integer,
    submitted_at timestamp without time zone,
    advisor_decided_at timestamp without time zone,
    admin_decided_at timestamp without time zone,
    note text
);


--
-- Name: registration_request_request_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.registration_request ALTER COLUMN request_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.registration_request_request_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: room; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room (
    room_id bigint NOT NULL,
    room_code character varying(30) NOT NULL,
    name character varying(100) NOT NULL,
    building character varying(150),
    capacity integer,
    network_mode character varying(30),
    ssid character varying(100),
    router_mac character varying(17),
    subnet character varying(50)
);


--
-- Name: room_room_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.room ALTER COLUMN room_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.room_room_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: student_grade; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_grade (
    student_grade_id bigint NOT NULL,
    grade_item_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    score numeric(6,2) NOT NULL,
    source_entry_id bigint,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    deleted_at timestamp without time zone,
    published_score numeric,
    published_at timestamp without time zone,
    CONSTRAINT chk_student_grade_published_score CHECK ((published_score >= (0)::numeric)),
    CONSTRAINT chk_student_grade_score CHECK ((score >= (0)::numeric))
);


--
-- Name: student_grade_student_grade_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.student_grade ALTER COLUMN student_grade_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.student_grade_student_grade_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: student_group; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_group (
    group_id bigint NOT NULL,
    offering_id bigint NOT NULL,
    group_type character varying(20) NOT NULL,
    label character varying(50) NOT NULL,
    day_of_week character varying(15),
    start_time time without time zone,
    end_time time without time zone
);


--
-- Name: student_group_group_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.student_group ALTER COLUMN group_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.student_group_group_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: student_profile; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.student_profile (
    user_id bigint NOT NULL,
    program_id bigint,
    advisor_id bigint,
    level integer,
    max_credit_hours integer
);


--
-- Name: submission; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.submission (
    submission_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    file_asset_id bigint NOT NULL,
    submitted_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    grade_item_id bigint NOT NULL,
    attempt_no integer DEFAULT 1 NOT NULL,
    late boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_submission_attempt_no CHECK ((attempt_no > 0))
);


--
-- Name: submission_submission_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.submission ALTER COLUMN submission_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.submission_submission_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: teaching_assignment; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.teaching_assignment (
    assignment_id bigint NOT NULL,
    group_id bigint NOT NULL,
    doctor_user_id bigint NOT NULL,
    starts_at timestamp without time zone NOT NULL,
    ends_at timestamp without time zone NOT NULL,
    CONSTRAINT chk_teaching_period CHECK (((ends_at IS NULL) OR (ends_at > starts_at)))
);


--
-- Name: teaching_assignment_assignment_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.teaching_assignment ALTER COLUMN assignment_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.teaching_assignment_assignment_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ticket; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ticket (
    ticket_id bigint NOT NULL,
    enrollment_id bigint NOT NULL,
    doctor_id bigint NOT NULL,
    category_id bigint NOT NULL,
    description text NOT NULL,
    status character varying(30) NOT NULL,
    decision character varying(30),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    resolved_at timestamp without time zone,
    title character varying(255),
    conversation_id bigint
);


--
-- Name: ticket_category; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ticket_category (
    category_id bigint NOT NULL,
    name character varying(100) NOT NULL,
    active boolean DEFAULT true NOT NULL
);


--
-- Name: ticket_category_category_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ticket_category ALTER COLUMN category_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ticket_category_category_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ticket_ticket_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ticket ALTER COLUMN ticket_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ticket_ticket_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: user_role; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_role (
    user_id bigint NOT NULL,
    role character varying(30) NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    user_id bigint NOT NULL,
    university_id character varying(50) NOT NULL,
    email character varying(255) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    full_name character varying(200) NOT NULL,
    full_name_ar character varying(200),
    password_hash character varying(255) NOT NULL,
    must_change_password boolean DEFAULT true NOT NULL,
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    token_version integer DEFAULT 0 NOT NULL,
    CONSTRAINT chk_users_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))
);


--
-- Name: users_user_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.users ALTER COLUMN user_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.users_user_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: academic_program academic_program_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_program
    ADD CONSTRAINT academic_program_code_key UNIQUE (code);


--
-- Name: academic_program academic_program_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_program
    ADD CONSTRAINT academic_program_pkey PRIMARY KEY (program_id);


--
-- Name: academic_term academic_term_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.academic_term
    ADD CONSTRAINT academic_term_pkey PRIMARY KEY (term_id);


--
-- Name: ai_citation ai_citation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_citation
    ADD CONSTRAINT ai_citation_pkey PRIMARY KEY (citation_id);


--
-- Name: ai_conversation ai_conversation_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_conversation
    ADD CONSTRAINT ai_conversation_pkey PRIMARY KEY (conversation_id);


--
-- Name: ai_message ai_message_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_message
    ADD CONSTRAINT ai_message_pkey PRIMARY KEY (message_id);


--
-- Name: assignment assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment
    ADD CONSTRAINT assignment_pkey PRIMARY KEY (grade_item_id);


--
-- Name: attendance_change attendance_change_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change
    ADD CONSTRAINT attendance_change_pkey PRIMARY KEY (change_id);


--
-- Name: attendance_record attendance_record_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_record
    ADD CONSTRAINT attendance_record_pkey PRIMARY KEY (record_id);


--
-- Name: attendance_session attendance_session_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_session
    ADD CONSTRAINT attendance_session_pkey PRIMARY KEY (session_id);


--
-- Name: attendance_warning attendance_warning_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning
    ADD CONSTRAINT attendance_warning_pkey PRIMARY KEY (warning_id);


--
-- Name: attendance_warning_rule attendance_warning_rule_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning_rule
    ADD CONSTRAINT attendance_warning_rule_pkey PRIMARY KEY (rule_id);


--
-- Name: audit_event audit_event_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event
    ADD CONSTRAINT audit_event_pkey PRIMARY KEY (event_id);


--
-- Name: control_export control_export_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_export
    ADD CONSTRAINT control_export_pkey PRIMARY KEY (export_id);


--
-- Name: course course_course_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_course_code_key UNIQUE (course_code);


--
-- Name: course_material course_material_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_material
    ADD CONSTRAINT course_material_pkey PRIMARY KEY (material_id);


--
-- Name: course_offering course_offering_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_offering
    ADD CONSTRAINT course_offering_pkey PRIMARY KEY (offering_id);


--
-- Name: course course_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course
    ADD CONSTRAINT course_pkey PRIMARY KEY (course_id);


--
-- Name: enrollment enrollment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT enrollment_pkey PRIMARY KEY (enrollment_id);


--
-- Name: file_asset file_asset_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_asset
    ADD CONSTRAINT file_asset_pkey PRIMARY KEY (file_asset_id);


--
-- Name: file_asset file_asset_storage_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_asset
    ADD CONSTRAINT file_asset_storage_key_key UNIQUE (storage_key);


--
-- Name: grade_change grade_change_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_change
    ADD CONSTRAINT grade_change_pkey PRIMARY KEY (change_id);


--
-- Name: grade_email grade_email_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_email
    ADD CONSTRAINT grade_email_pkey PRIMARY KEY (grade_email_id);


--
-- Name: grade_import_batch grade_import_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_batch
    ADD CONSTRAINT grade_import_batch_pkey PRIMARY KEY (batch_id);


--
-- Name: grade_import_entry grade_import_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_entry
    ADD CONSTRAINT grade_import_entry_pkey PRIMARY KEY (entry_id);


--
-- Name: grade_import_sheet grade_import_sheet_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_sheet
    ADD CONSTRAINT grade_import_sheet_pkey PRIMARY KEY (sheet_id);


--
-- Name: grade_item_group grade_item_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_item_group
    ADD CONSTRAINT grade_item_group_pkey PRIMARY KEY (grade_item_id, group_id);


--
-- Name: grade_item grade_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_item
    ADD CONSTRAINT grade_item_pkey PRIMARY KEY (grade_item_id);


--
-- Name: grade_send_batch grade_send_batch_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_send_batch
    ADD CONSTRAINT grade_send_batch_pkey PRIMARY KEY (send_batch_id);


--
-- Name: group_membership group_membership_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT group_membership_pkey PRIMARY KEY (membership_id);


--
-- Name: knowledge_chunk knowledge_chunk_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunk
    ADD CONSTRAINT knowledge_chunk_pkey PRIMARY KEY (chunk_id);


--
-- Name: password_reset_token password_reset_token_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_token
    ADD CONSTRAINT password_reset_token_pkey PRIMARY KEY (token_id);


--
-- Name: assignment_attachment pk_assignment_attachment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_attachment
    ADD CONSTRAINT pk_assignment_attachment PRIMARY KEY (grade_item_id, file_asset_id);


--
-- Name: user_role pk_user_role; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT pk_user_role PRIMARY KEY (user_id, role);


--
-- Name: program_plan_course program_plan_course_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.program_plan_course
    ADD CONSTRAINT program_plan_course_pkey PRIMARY KEY (plan_course_id);


--
-- Name: qr_challenge qr_challenge_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_challenge
    ADD CONSTRAINT qr_challenge_pkey PRIMARY KEY (challenge_id);


--
-- Name: registration_item registration_item_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_item
    ADD CONSTRAINT registration_item_pkey PRIMARY KEY (item_id);


--
-- Name: registration_request registration_request_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT registration_request_pkey PRIMARY KEY (request_id);


--
-- Name: room room_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT room_pkey PRIMARY KEY (room_id);


--
-- Name: room room_room_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room
    ADD CONSTRAINT room_room_code_key UNIQUE (room_code);


--
-- Name: student_grade student_grade_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_grade
    ADD CONSTRAINT student_grade_pkey PRIMARY KEY (student_grade_id);


--
-- Name: student_group student_group_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_group
    ADD CONSTRAINT student_group_pkey PRIMARY KEY (group_id);


--
-- Name: student_profile student_profile_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profile
    ADD CONSTRAINT student_profile_pkey PRIMARY KEY (user_id);


--
-- Name: submission submission_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT submission_pkey PRIMARY KEY (submission_id);


--
-- Name: teaching_assignment teaching_assignment_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_assignment
    ADD CONSTRAINT teaching_assignment_pkey PRIMARY KEY (assignment_id);


--
-- Name: ticket_category ticket_category_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket_category
    ADD CONSTRAINT ticket_category_name_key UNIQUE (name);


--
-- Name: ticket_category ticket_category_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket_category
    ADD CONSTRAINT ticket_category_pkey PRIMARY KEY (category_id);


--
-- Name: ticket ticket_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket
    ADD CONSTRAINT ticket_pkey PRIMARY KEY (ticket_id);


--
-- Name: attendance_warning uq_attendance_warning; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning
    ADD CONSTRAINT uq_attendance_warning UNIQUE (enrollment_id, rule_id);


--
-- Name: course_offering uq_course_offering_section; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_offering
    ADD CONSTRAINT uq_course_offering_section UNIQUE (course_id, term_id, section);


--
-- Name: grade_email uq_grade_email_batch_enrollment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_email
    ADD CONSTRAINT uq_grade_email_batch_enrollment UNIQUE (send_batch_id, enrollment_id);


--
-- Name: group_membership uq_group_membership_enrollment_group; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT uq_group_membership_enrollment_group UNIQUE (enrollment_id, group_id);


--
-- Name: program_plan_course uq_program_plan_course; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.program_plan_course
    ADD CONSTRAINT uq_program_plan_course UNIQUE (program_id, level, term_type, course_id);


--
-- Name: registration_item uq_registration_item; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_item
    ADD CONSTRAINT uq_registration_item UNIQUE (request_id, offering_id);


--
-- Name: registration_request uq_registration_student_term; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT uq_registration_student_term UNIQUE (student_user_id, term_id);


--
-- Name: attendance_record uq_session_enrollment; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_record
    ADD CONSTRAINT uq_session_enrollment UNIQUE (session_id, enrollment_id);


--
-- Name: student_grade uq_student_grade; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_grade
    ADD CONSTRAINT uq_student_grade UNIQUE (grade_item_id, enrollment_id);


--
-- Name: enrollment uq_student_offering; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT uq_student_offering UNIQUE (student_user_id, offering_id);


--
-- Name: submission uq_submission_grade_item_enrollment_attempt; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT uq_submission_grade_item_enrollment_attempt UNIQUE (grade_item_id, enrollment_id, attempt_no);


--
-- Name: attendance_warning_rule uq_warning_rule; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning_rule
    ADD CONSTRAINT uq_warning_rule UNIQUE (offering_id, group_type);


--
-- Name: users users_email_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_email_key UNIQUE (email);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (user_id);


--
-- Name: users users_university_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_university_id_key UNIQUE (university_id);


--
-- Name: idx_knowledge_chunk_embedding; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_knowledge_chunk_embedding ON public.knowledge_chunk USING hnsw (embedding public.vector_cosine_ops);


--
-- Name: ai_conversation fk_ai_conversation_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_conversation
    ADD CONSTRAINT fk_ai_conversation_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: assignment fk_assignment_grade_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment
    ADD CONSTRAINT fk_assignment_grade_item FOREIGN KEY (grade_item_id) REFERENCES public.grade_item(grade_item_id);


--
-- Name: assignment_attachment fk_attachment_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_attachment
    ADD CONSTRAINT fk_attachment_assignment FOREIGN KEY (grade_item_id) REFERENCES public.assignment(grade_item_id);


--
-- Name: assignment_attachment fk_attachment_file; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignment_attachment
    ADD CONSTRAINT fk_attachment_file FOREIGN KEY (file_asset_id) REFERENCES public.file_asset(file_asset_id);


--
-- Name: attendance_session fk_attendance_session_teaching_assignment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_session
    ADD CONSTRAINT fk_attendance_session_teaching_assignment FOREIGN KEY (teaching_assignment_id) REFERENCES public.teaching_assignment(assignment_id);


--
-- Name: audit_event fk_audit_actor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_event
    ADD CONSTRAINT fk_audit_actor FOREIGN KEY (actor_id) REFERENCES public.users(user_id);


--
-- Name: attendance_change fk_change_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change
    ADD CONSTRAINT fk_change_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: attendance_change fk_change_record; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_change
    ADD CONSTRAINT fk_change_record FOREIGN KEY (record_id) REFERENCES public.attendance_record(record_id);


--
-- Name: knowledge_chunk fk_chunk_material; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.knowledge_chunk
    ADD CONSTRAINT fk_chunk_material FOREIGN KEY (material_id) REFERENCES public.course_material(material_id);


--
-- Name: ai_citation fk_citation_chunk; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_citation
    ADD CONSTRAINT fk_citation_chunk FOREIGN KEY (chunk_id) REFERENCES public.knowledge_chunk(chunk_id);


--
-- Name: ai_citation fk_citation_message; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_citation
    ADD CONSTRAINT fk_citation_message FOREIGN KEY (message_id) REFERENCES public.ai_message(message_id);


--
-- Name: ai_conversation fk_conversation_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_conversation
    ADD CONSTRAINT fk_conversation_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: enrollment fk_enrollment_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT fk_enrollment_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: enrollment fk_enrollment_program; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT fk_enrollment_program FOREIGN KEY (program_id) REFERENCES public.academic_program(program_id);


--
-- Name: enrollment fk_enrollment_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.enrollment
    ADD CONSTRAINT fk_enrollment_student FOREIGN KEY (student_user_id) REFERENCES public.users(user_id);


--
-- Name: control_export fk_export_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_export
    ADD CONSTRAINT fk_export_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: control_export fk_export_file; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_export
    ADD CONSTRAINT fk_export_file FOREIGN KEY (file_asset_id) REFERENCES public.file_asset(file_asset_id);


--
-- Name: control_export fk_export_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.control_export
    ADD CONSTRAINT fk_export_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: file_asset fk_file_uploader; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.file_asset
    ADD CONSTRAINT fk_file_uploader FOREIGN KEY (uploader_id) REFERENCES public.users(user_id);


--
-- Name: grade_change fk_grade_change_batch; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_change
    ADD CONSTRAINT fk_grade_change_batch FOREIGN KEY (batch_id) REFERENCES public.grade_import_batch(batch_id);


--
-- Name: grade_change fk_grade_change_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_change
    ADD CONSTRAINT fk_grade_change_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: grade_change fk_grade_change_student_grade; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_change
    ADD CONSTRAINT fk_grade_change_student_grade FOREIGN KEY (student_grade_id) REFERENCES public.student_grade(student_grade_id);


--
-- Name: grade_email fk_grade_email_batch; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_email
    ADD CONSTRAINT fk_grade_email_batch FOREIGN KEY (send_batch_id) REFERENCES public.grade_send_batch(send_batch_id);


--
-- Name: grade_email fk_grade_email_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_email
    ADD CONSTRAINT fk_grade_email_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: grade_item_group fk_grade_group_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_item_group
    ADD CONSTRAINT fk_grade_group_item FOREIGN KEY (grade_item_id) REFERENCES public.grade_item(grade_item_id);


--
-- Name: grade_item_group fk_grade_group_student_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_item_group
    ADD CONSTRAINT fk_grade_group_student_group FOREIGN KEY (group_id) REFERENCES public.student_group(group_id);


--
-- Name: grade_item fk_grade_item_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_item
    ADD CONSTRAINT fk_grade_item_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: student_group fk_group_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_group
    ADD CONSTRAINT fk_group_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: grade_import_batch fk_import_batch_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_batch
    ADD CONSTRAINT fk_import_batch_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: grade_import_batch fk_import_batch_file; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_batch
    ADD CONSTRAINT fk_import_batch_file FOREIGN KEY (file_asset_id) REFERENCES public.file_asset(file_asset_id);


--
-- Name: grade_import_batch fk_import_batch_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_batch
    ADD CONSTRAINT fk_import_batch_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: grade_import_entry fk_import_entry_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_entry
    ADD CONSTRAINT fk_import_entry_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: grade_import_entry fk_import_entry_grade_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_entry
    ADD CONSTRAINT fk_import_entry_grade_item FOREIGN KEY (grade_item_id) REFERENCES public.grade_item(grade_item_id);


--
-- Name: grade_import_entry fk_import_entry_sheet; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_entry
    ADD CONSTRAINT fk_import_entry_sheet FOREIGN KEY (sheet_id) REFERENCES public.grade_import_sheet(sheet_id);


--
-- Name: grade_import_sheet fk_import_sheet_batch; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_sheet
    ADD CONSTRAINT fk_import_sheet_batch FOREIGN KEY (batch_id) REFERENCES public.grade_import_batch(batch_id);


--
-- Name: grade_import_sheet fk_import_sheet_program; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_import_sheet
    ADD CONSTRAINT fk_import_sheet_program FOREIGN KEY (program_id) REFERENCES public.academic_program(program_id);


--
-- Name: course_material fk_material_file; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_material
    ADD CONSTRAINT fk_material_file FOREIGN KEY (file_asset_id) REFERENCES public.file_asset(file_asset_id);


--
-- Name: course_material fk_material_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_material
    ADD CONSTRAINT fk_material_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: group_membership fk_membership_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT fk_membership_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: group_membership fk_membership_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.group_membership
    ADD CONSTRAINT fk_membership_group FOREIGN KEY (group_id) REFERENCES public.student_group(group_id);


--
-- Name: ai_message fk_message_conversation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ai_message
    ADD CONSTRAINT fk_message_conversation FOREIGN KEY (conversation_id) REFERENCES public.ai_conversation(conversation_id);


--
-- Name: course_offering fk_offering_course; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_offering
    ADD CONSTRAINT fk_offering_course FOREIGN KEY (course_id) REFERENCES public.course(course_id);


--
-- Name: course_offering fk_offering_term; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.course_offering
    ADD CONSTRAINT fk_offering_term FOREIGN KEY (term_id) REFERENCES public.academic_term(term_id);


--
-- Name: password_reset_token fk_password_reset_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.password_reset_token
    ADD CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: program_plan_course fk_plan_course_course; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.program_plan_course
    ADD CONSTRAINT fk_plan_course_course FOREIGN KEY (course_id) REFERENCES public.course(course_id);


--
-- Name: program_plan_course fk_plan_course_program; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.program_plan_course
    ADD CONSTRAINT fk_plan_course_program FOREIGN KEY (program_id) REFERENCES public.academic_program(program_id);


--
-- Name: qr_challenge fk_qr_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.qr_challenge
    ADD CONSTRAINT fk_qr_session FOREIGN KEY (session_id) REFERENCES public.attendance_session(session_id);


--
-- Name: attendance_record fk_record_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_record
    ADD CONSTRAINT fk_record_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: attendance_record fk_record_session; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_record
    ADD CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES public.attendance_session(session_id);


--
-- Name: registration_request fk_registration_admin; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT fk_registration_admin FOREIGN KEY (admin_id) REFERENCES public.users(user_id);


--
-- Name: registration_request fk_registration_advisor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT fk_registration_advisor FOREIGN KEY (advisor_id) REFERENCES public.users(user_id);


--
-- Name: registration_item fk_registration_item_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_item
    ADD CONSTRAINT fk_registration_item_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: registration_item fk_registration_item_request; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_item
    ADD CONSTRAINT fk_registration_item_request FOREIGN KEY (request_id) REFERENCES public.registration_request(request_id);


--
-- Name: registration_request fk_registration_student; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT fk_registration_student FOREIGN KEY (student_user_id) REFERENCES public.users(user_id);


--
-- Name: registration_request fk_registration_term; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.registration_request
    ADD CONSTRAINT fk_registration_term FOREIGN KEY (term_id) REFERENCES public.academic_term(term_id);


--
-- Name: grade_send_batch fk_send_batch_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_send_batch
    ADD CONSTRAINT fk_send_batch_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: grade_send_batch fk_send_batch_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.grade_send_batch
    ADD CONSTRAINT fk_send_batch_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- Name: attendance_session fk_session_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_session
    ADD CONSTRAINT fk_session_group FOREIGN KEY (group_id) REFERENCES public.student_group(group_id);


--
-- Name: attendance_session fk_session_room; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_session
    ADD CONSTRAINT fk_session_room FOREIGN KEY (room_id) REFERENCES public.room(room_id);


--
-- Name: student_grade fk_student_grade_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_grade
    ADD CONSTRAINT fk_student_grade_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: student_grade fk_student_grade_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_grade
    ADD CONSTRAINT fk_student_grade_item FOREIGN KEY (grade_item_id) REFERENCES public.grade_item(grade_item_id);


--
-- Name: student_grade fk_student_grade_source; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_grade
    ADD CONSTRAINT fk_student_grade_source FOREIGN KEY (source_entry_id) REFERENCES public.grade_import_entry(entry_id);


--
-- Name: student_profile fk_student_profile_advisor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profile
    ADD CONSTRAINT fk_student_profile_advisor FOREIGN KEY (advisor_id) REFERENCES public.users(user_id);


--
-- Name: student_profile fk_student_profile_program; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profile
    ADD CONSTRAINT fk_student_profile_program FOREIGN KEY (program_id) REFERENCES public.academic_program(program_id);


--
-- Name: student_profile fk_student_profile_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.student_profile
    ADD CONSTRAINT fk_student_profile_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: submission fk_submission_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT fk_submission_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: submission fk_submission_file; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT fk_submission_file FOREIGN KEY (file_asset_id) REFERENCES public.file_asset(file_asset_id);


--
-- Name: submission fk_submission_grade_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.submission
    ADD CONSTRAINT fk_submission_grade_item FOREIGN KEY (grade_item_id) REFERENCES public.grade_item(grade_item_id);


--
-- Name: teaching_assignment fk_teaching_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_assignment
    ADD CONSTRAINT fk_teaching_doctor FOREIGN KEY (doctor_user_id) REFERENCES public.users(user_id);


--
-- Name: teaching_assignment fk_teaching_group; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.teaching_assignment
    ADD CONSTRAINT fk_teaching_group FOREIGN KEY (group_id) REFERENCES public.student_group(group_id);


--
-- Name: ticket fk_ticket_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket
    ADD CONSTRAINT fk_ticket_category FOREIGN KEY (category_id) REFERENCES public.ticket_category(category_id);


--
-- Name: ticket fk_ticket_conversation; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket
    ADD CONSTRAINT fk_ticket_conversation FOREIGN KEY (conversation_id) REFERENCES public.ai_conversation(conversation_id);


--
-- Name: ticket fk_ticket_doctor; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket
    ADD CONSTRAINT fk_ticket_doctor FOREIGN KEY (doctor_id) REFERENCES public.users(user_id);


--
-- Name: ticket fk_ticket_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ticket
    ADD CONSTRAINT fk_ticket_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: user_role fk_user_role_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_role
    ADD CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES public.users(user_id);


--
-- Name: attendance_warning fk_warning_enrollment; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning
    ADD CONSTRAINT fk_warning_enrollment FOREIGN KEY (enrollment_id) REFERENCES public.enrollment(enrollment_id);


--
-- Name: attendance_warning fk_warning_rule; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning
    ADD CONSTRAINT fk_warning_rule FOREIGN KEY (rule_id) REFERENCES public.attendance_warning_rule(rule_id);


--
-- Name: attendance_warning_rule fk_warning_rule_offering; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance_warning_rule
    ADD CONSTRAINT fk_warning_rule_offering FOREIGN KEY (offering_id) REFERENCES public.course_offering(offering_id);


--
-- PostgreSQL database dump complete
--
