-- SentinelCV Database Schema
-- SQLite Database Initialization

-- Enable WAL mode for better concurrent read performance
PRAGMA journal_mode=WAL;
PRAGMA foreign_keys=ON;

-- ============================================================
-- Dataset Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS datasets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    format TEXT NOT NULL CHECK(format IN ('COCO', 'YOLO')),
    path TEXT NOT NULL,
    total_images INTEGER DEFAULT 0,
    total_annotations INTEGER DEFAULT 0,
    total_classes INTEGER DEFAULT 0,
    file_size_bytes INTEGER DEFAULT 0,
    sha256_hash TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_datasets_name ON datasets(name);
CREATE INDEX IF NOT EXISTS idx_datasets_format ON datasets(format);

CREATE TABLE IF NOT EXISTS dataset_audits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    dataset_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    started_at TEXT,
    completed_at TEXT,
    total_findings INTEGER DEFAULT 0,
    critical_findings INTEGER DEFAULT 0,
    high_findings INTEGER DEFAULT 0,
    medium_findings INTEGER DEFAULT 0,
    low_findings INTEGER DEFAULT 0,
    info_findings INTEGER DEFAULT 0,
    integrity_score REAL DEFAULT 0.0,
    assessment_decision TEXT CHECK(assessment_decision IN ('ACCEPT','REVIEW','QUARANTINE')),
    summary TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

CREATE INDEX IF NOT EXISTS idx_dataset_audits_dataset_id ON dataset_audits(dataset_id);
CREATE INDEX IF NOT EXISTS idx_dataset_audits_status ON dataset_audits(status);

CREATE TABLE IF NOT EXISTS dataset_findings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    audit_id INTEGER NOT NULL,
    dataset_id INTEGER NOT NULL,
    finding_id TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    severity TEXT NOT NULL CHECK(severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    confidence REAL DEFAULT 0.0,
    evidence TEXT,
    affected_asset TEXT,
    recommended_action TEXT,
    detected_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (audit_id) REFERENCES dataset_audits(id),
    FOREIGN KEY (dataset_id) REFERENCES datasets(id)
);

CREATE INDEX IF NOT EXISTS idx_dataset_findings_audit_id ON dataset_findings(audit_id);
CREATE INDEX IF NOT EXISTS idx_dataset_findings_severity ON dataset_findings(severity);

-- ============================================================
-- Model Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS models (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    format TEXT NOT NULL CHECK(format IN ('ONNX', 'TORCHSCRIPT', 'UNKNOWN')),
    path TEXT NOT NULL,
    file_size_bytes INTEGER DEFAULT 0,
    sha256_hash TEXT,
    metadata_json TEXT,
    input_info TEXT,
    output_info TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_models_name ON models(name);
CREATE INDEX IF NOT EXISTS idx_models_format ON models(format);

CREATE TABLE IF NOT EXISTS model_audits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    model_id INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','RUNNING','COMPLETED','FAILED','CANCELLED')),
    started_at TEXT,
    completed_at TEXT,
    total_findings INTEGER DEFAULT 0,
    critical_findings INTEGER DEFAULT 0,
    high_findings INTEGER DEFAULT 0,
    medium_findings INTEGER DEFAULT 0,
    low_findings INTEGER DEFAULT 0,
    info_findings INTEGER DEFAULT 0,
    behavioral_fingerprint TEXT,
    assessment_decision TEXT CHECK(assessment_decision IN ('ACCEPT','REVIEW','QUARANTINE')),
    summary TEXT,
    error_message TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (model_id) REFERENCES models(id)
);

CREATE INDEX IF NOT EXISTS idx_model_audits_model_id ON model_audits(model_id);
CREATE INDEX IF NOT EXISTS idx_model_audits_status ON model_audits(status);

CREATE TABLE IF NOT EXISTS model_findings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    audit_id INTEGER NOT NULL,
    model_id INTEGER NOT NULL,
    finding_id TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    severity TEXT NOT NULL CHECK(severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    confidence REAL DEFAULT 0.0,
    evidence TEXT,
    affected_asset TEXT,
    recommended_action TEXT,
    detected_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (audit_id) REFERENCES model_audits(id),
    FOREIGN KEY (model_id) REFERENCES models(id)
);

CREATE INDEX IF NOT EXISTS idx_model_findings_audit_id ON model_findings(audit_id);
CREATE INDEX IF NOT EXISTS idx_model_findings_severity ON model_findings(severity);

-- ============================================================
-- Inference Provenance Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS inference_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id TEXT NOT NULL UNIQUE,
    timestamp TEXT NOT NULL,
    model_identifier TEXT,
    model_hash TEXT,
    input_image_hash TEXT,
    config_hash TEXT,
    output_hash TEXT,
    nonce TEXT,
    sequence_number INTEGER,
    previous_record_hash TEXT,
    current_record_hash TEXT,
    signature TEXT,
    hmac_value TEXT,
    raw_output TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_inference_records_record_id ON inference_records(record_id);
CREATE INDEX IF NOT EXISTS idx_inference_records_sequence ON inference_records(sequence_number);
CREATE INDEX IF NOT EXISTS idx_inference_records_nonce ON inference_records(nonce);

CREATE TABLE IF NOT EXISTS provenance_verifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    record_id TEXT NOT NULL,
    input_hash_status TEXT DEFAULT 'PENDING',
    model_hash_status TEXT DEFAULT 'PENDING',
    config_hash_status TEXT DEFAULT 'PENDING',
    output_hash_status TEXT DEFAULT 'PENDING',
    chain_link_status TEXT DEFAULT 'PENDING',
    nonce_status TEXT DEFAULT 'PENDING',
    sequence_status TEXT DEFAULT 'PENDING',
    signature_status TEXT DEFAULT 'PENDING',
    timestamp_status TEXT DEFAULT 'PENDING',
    overall_status TEXT DEFAULT 'PENDING',
    details TEXT,
    verified_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (record_id) REFERENCES inference_records(record_id)
);

CREATE INDEX IF NOT EXISTS idx_provenance_record_id ON provenance_verifications(record_id);

-- ============================================================
-- Drift Analysis Tables
-- ============================================================

CREATE TABLE IF NOT EXISTS drift_audits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    reference_dataset_id INTEGER,
    evaluation_dataset_id INTEGER,
    status TEXT NOT NULL DEFAULT 'PENDING',
    drift_detected INTEGER DEFAULT 0,
    overall_drift_score REAL DEFAULT 0.0,
    severity TEXT,
    metrics_json TEXT,
    summary TEXT,
    started_at TEXT,
    completed_at TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    FOREIGN KEY (reference_dataset_id) REFERENCES datasets(id),
    FOREIGN KEY (evaluation_dataset_id) REFERENCES datasets(id)
);

CREATE INDEX IF NOT EXISTS idx_drift_audits_status ON drift_audits(status);

-- ============================================================
-- Security Findings (Aggregated)
-- ============================================================

CREATE TABLE IF NOT EXISTS security_findings (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    finding_id TEXT NOT NULL UNIQUE,
    module TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    severity TEXT NOT NULL CHECK(severity IN ('CRITICAL','HIGH','MEDIUM','LOW','INFO')),
    confidence REAL DEFAULT 0.0,
    evidence TEXT,
    affected_asset TEXT,
    recommended_action TEXT,
    source_audit_id INTEGER,
    source_audit_type TEXT,
    detected_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_security_findings_severity ON security_findings(severity);
CREATE INDEX IF NOT EXISTS idx_security_findings_module ON security_findings(module);

-- ============================================================
-- Audit Reports
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_reports (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    title TEXT NOT NULL,
    report_type TEXT NOT NULL,
    format TEXT NOT NULL DEFAULT 'JSON',
    file_path TEXT,
    assessment_decision TEXT,
    total_findings INTEGER DEFAULT 0,
    critical_count INTEGER DEFAULT 0,
    high_count INTEGER DEFAULT 0,
    medium_count INTEGER DEFAULT 0,
    low_count INTEGER DEFAULT 0,
    info_count INTEGER DEFAULT 0,
    summary TEXT,
    generated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_audit_reports_type ON audit_reports(report_type);

-- ============================================================
-- Audit History
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    audit_type TEXT NOT NULL,
    target_name TEXT NOT NULL,
    target_id INTEGER,
    status TEXT NOT NULL,
    findings_count INTEGER DEFAULT 0,
    assessment_decision TEXT,
    started_at TEXT,
    completed_at TEXT,
    summary TEXT,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_audit_history_type ON audit_history(audit_type);
CREATE INDEX IF NOT EXISTS idx_audit_history_created ON audit_history(created_at);

-- ============================================================
-- Application Settings
-- ============================================================

CREATE TABLE IF NOT EXISTS app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    description TEXT,
    updated_at TEXT NOT NULL DEFAULT (datetime('now'))
);

-- Default settings
INSERT OR IGNORE INTO app_settings (key, value, description) VALUES
    ('perceptual_hash_threshold', '10', 'Hamming distance threshold for near-duplicate detection'),
    ('duplicate_similarity_threshold', '0.95', 'Similarity threshold for image duplicate detection'),
    ('max_image_size_mb', '50', 'Maximum allowed image file size in MB'),
    ('max_model_size_mb', '2048', 'Maximum allowed model file size in MB'),
    ('thread_pool_size', '4', 'Number of threads for parallel processing'),
    ('quarantine_score', '80', 'Risk score threshold for QUARANTINE decision'),
    ('review_score', '40', 'Risk score threshold for REVIEW decision'),
    ('drift_significance_threshold', '0.05', 'P-value threshold for drift significance'),
    ('psi_threshold', '0.2', 'Population Stability Index threshold for drift detection');

-- ============================================================
-- Mandatory Check Statuses (Immutable Asset Version Association)
-- ============================================================

CREATE TABLE IF NOT EXISTS mandatory_check_statuses (
    check_id TEXT PRIMARY KEY,
    audit_id INTEGER,
    asset_id TEXT,
    asset_hash TEXT,
    execution_timestamp TEXT NOT NULL,
    execution_status TEXT NOT NULL,
    details TEXT
);
