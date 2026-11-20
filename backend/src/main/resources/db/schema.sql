-- LegalCase ERP schema - mirrors Assignment 2 section 10 + FR1-FR11 extensions.
-- Works on H2 (default) and PostgreSQL.

CREATE TABLE IF NOT EXISTS users (
    id            VARCHAR(32)  PRIMARY KEY,
    name          VARCHAR(200) NOT NULL,
    email         VARCHAR(200) UNIQUE NOT NULL,
    password_hash VARCHAR(400) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    created_at    TIMESTAMP
);
ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS cases (
    id              VARCHAR(32)  PRIMARY KEY,
    reference       VARCHAR(50)  UNIQUE NOT NULL,
    title           VARCHAR(300) NOT NULL,
    client_id       VARCHAR(32),
    client_name     VARCHAR(200),
    opposing_party  VARCHAR(200),
    status          VARCHAR(40),
    matter_type     VARCHAR(120),
    court           VARCHAR(200),
    description     VARCHAR(2000),
    opened_date     DATE,
    amount_involved DECIMAL(18,2)
);
ALTER TABLE cases ADD COLUMN IF NOT EXISTS direct_payments DECIMAL(18,2) DEFAULT 0;

CREATE TABLE IF NOT EXISTS documents (
    id           VARCHAR(32)  PRIMARY KEY,
    case_id      VARCHAR(32),
    filename     VARCHAR(300),
    storage_path VARCHAR(600),
    uploaded_by  VARCHAR(200),
    uploaded_at  TIMESTAMP,
    size_kb      BIGINT,
    status       VARCHAR(20)
);
ALTER TABLE documents ADD COLUMN IF NOT EXISTS doc_type VARCHAR(10) DEFAULT 'PDF';
ALTER TABLE documents ADD COLUMN IF NOT EXISTS consent_granted BOOLEAN DEFAULT TRUE;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS full_text CLOB;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS rejection_reason VARCHAR(400) DEFAULT NULL;

CREATE TABLE IF NOT EXISTS extractions (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id VARCHAR(32),
    field_name  VARCHAR(80),
    field_value VARCHAR(400),
    confidence  DOUBLE PRECISION,
    span_text   VARCHAR(600),
    page_number INT
);

CREATE TABLE IF NOT EXISTS summaries (
    id          VARCHAR(32) PRIMARY KEY,
    document_id VARCHAR(32),
    case_id     VARCHAR(32),
    paragraph1  CLOB,
    paragraph2  CLOB,
    paragraph3  CLOB,
    version     INT,
    status      VARCHAR(20),
    created_by  VARCHAR(200),
    created_at  TIMESTAMP,
    approved_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS deadlines (
    id            VARCHAR(32) PRIMARY KEY,
    case_id       VARCHAR(32),
    title         VARCHAR(300),
    deadline_date DATE,
    dl_type       VARCHAR(20),
    reminder_days INT,
    synced        BOOLEAN DEFAULT FALSE
);

CREATE TABLE IF NOT EXISTS messages (
    id             VARCHAR(32) PRIMARY KEY,
    case_id        VARCHAR(32),
    from_name      VARCHAR(200),
    subject        VARCHAR(300),
    body           VARCHAR(4000),
    sent_at        TIMESTAMP,
    from_lawyer    BOOLEAN,
    read_by_client BOOLEAN DEFAULT FALSE
);
ALTER TABLE messages ADD COLUMN IF NOT EXISTS read_by_lawyer BOOLEAN DEFAULT FALSE;

-- Tamper-evident audit log (FR10/FR11): every row carries a hash of its own
-- content + the previous row's hash, forming a chain that makes retroactive
-- edits detectable.
CREATE TABLE IF NOT EXISTS audit_log (
    id        INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ts        TIMESTAMP,
    user_name VARCHAR(200),
    action    VARCHAR(60),
    details   VARCHAR(1200),
    level     VARCHAR(10)
);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS prev_hash VARCHAR(64) DEFAULT '';
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS hash      VARCHAR(64) DEFAULT '';

CREATE TABLE IF NOT EXISTS calendar_events (
    id          VARCHAR(32) PRIMARY KEY,
    deadline_id VARCHAR(32),
    event_id    VARCHAR(200),
    calendar_id VARCHAR(200),
    created_at  TIMESTAMP
);

CREATE TABLE IF NOT EXISTS processing_jobs (
    id          INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_id VARCHAR(32),
    status      VARCHAR(20),
    error       VARCHAR(1200),
    created_at  TIMESTAMP,
    finished_at TIMESTAMP
);

-- Client ratings of lawyers (FR7)
CREATE TABLE IF NOT EXISTS ratings (
    id          VARCHAR(32) PRIMARY KEY,
    case_id     VARCHAR(32),
    client_id   VARCHAR(32),
    lawyer_name VARCHAR(200),
    score       INT,
    comment     VARCHAR(1000),
    created_at  TIMESTAMP
);

-- Model retraining runs (FR10 / ETR3)
CREATE TABLE IF NOT EXISTS training_runs (
    id          VARCHAR(32) PRIMARY KEY,
    model_name  VARCHAR(200),
    dataset_id  VARCHAR(32),
    status      VARCHAR(20),
    requested_by VARCHAR(200),
    requested_at TIMESTAMP,
    approved_at  TIMESTAMP,
    accuracy     DOUBLE PRECISION
);

-- Training datasets (FR10 / ETR3)
CREATE TABLE IF NOT EXISTS datasets (
    id         VARCHAR(32) PRIMARY KEY,
    name       VARCHAR(300),
    records    BIGINT,
    size_kb    BIGINT,
    uploaded_at TIMESTAMP
);
ALTER TABLE datasets ADD COLUMN IF NOT EXISTS storage_path VARCHAR(600) DEFAULT '';

-- Practitioner AI corrections harvested for human-in-the-loop retraining (ETR-04)
CREATE TABLE IF NOT EXISTS ai_corrections (
    id          VARCHAR(32) PRIMARY KEY,
    summary_id  VARCHAR(32),
    document_id VARCHAR(32),
    case_id     VARCHAR(32),
    edited_by   VARCHAR(200),
    edited_at   TIMESTAMP,
    orig_p1     CLOB,
    orig_p2     CLOB,
    orig_p3     CLOB,
    new_p1      CLOB,
    new_p2      CLOB,
    new_p3      CLOB,
    dataset_id  VARCHAR(32)
);

-- Time tracking, disbursements and fee-note billing entries
CREATE TABLE IF NOT EXISTS billing_entries (
    id           VARCHAR(32) PRIMARY KEY,
    case_id      VARCHAR(32) NOT NULL,
    entry_date   DATE NOT NULL,
    entry_type   VARCHAR(20) NOT NULL,
    description  VARCHAR(500) NOT NULL,
    hours        DECIMAL(10,2) DEFAULT 0,
    hourly_rate  DECIMAL(12,2) DEFAULT 0,
    amount       DECIMAL(12,2) NOT NULL,
    recorded_by  VARCHAR(200),
    created_at   TIMESTAMP
);

-- Encrypted backup metadata and measured restore verification times (NFR-02)
CREATE TABLE IF NOT EXISTS system_backups (
    id                 VARCHAR(32) PRIMARY KEY,
    filename           VARCHAR(300) NOT NULL,
    created_by         VARCHAR(200) NOT NULL,
    created_at         TIMESTAMP NOT NULL,
    size_bytes         BIGINT NOT NULL,
    total_rows         BIGINT NOT NULL,
    backup_ms          DOUBLE PRECISION NOT NULL,
    restore_verify_ms  DOUBLE PRECISION NOT NULL,
    verified           BOOLEAN DEFAULT TRUE
);

-- Section 86 Legal Practice Act client trust-account ledger (never allowed into debit)
CREATE TABLE IF NOT EXISTS trust_ledger (
    id            VARCHAR(32) PRIMARY KEY,
    case_id       VARCHAR(32) NOT NULL,
    tx_date       DATE NOT NULL,
    tx_type       VARCHAR(30) NOT NULL,
    description   VARCHAR(500) NOT NULL,
    reference     VARCHAR(120),
    amount        DECIMAL(14,2) NOT NULL,
    balance_after DECIMAL(14,2) NOT NULL,
    recorded_by   VARCHAR(200),
    created_at    TIMESTAMP
);

-- Email reminders for upcoming deadlines (one row per deadline and recipient)
CREATE TABLE IF NOT EXISTS reminders (
    id              VARCHAR(40) PRIMARY KEY,
    deadline_id     VARCHAR(40) NOT NULL,
    case_reference  VARCHAR(40),
    recipient       VARCHAR(200) NOT NULL,
    subject         VARCHAR(300) NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    error           VARCHAR(500),
    created_at      TIMESTAMP NOT NULL,
    sent_at         TIMESTAMP,
    CONSTRAINT uq_reminder_deadline_recipient UNIQUE (deadline_id, recipient)
);
CREATE INDEX IF NOT EXISTS idx_reminders_status ON reminders(status);

CREATE INDEX IF NOT EXISTS idx_documents_case    ON documents(case_id);
CREATE INDEX IF NOT EXISTS idx_extractions_doc   ON extractions(document_id);
CREATE INDEX IF NOT EXISTS idx_summaries_doc     ON summaries(document_id);
CREATE INDEX IF NOT EXISTS idx_deadlines_case    ON deadlines(case_id);
CREATE INDEX IF NOT EXISTS idx_messages_case     ON messages(case_id);
CREATE INDEX IF NOT EXISTS idx_audit_ts          ON audit_log(ts);
CREATE INDEX IF NOT EXISTS idx_billing_case      ON billing_entries(case_id);
CREATE INDEX IF NOT EXISTS idx_trust_case        ON trust_ledger(case_id);
