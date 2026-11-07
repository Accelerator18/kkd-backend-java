--liquibase formatted sql
--changeset kkd:001-schema
CREATE TABLE org_unit (
    id VARCHAR(40) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id VARCHAR(40) REFERENCES org_unit(id),
    CHECK (parent_id IS DISTINCT FROM id)
);
CREATE INDEX idx_unit_parent ON org_unit(parent_id);
CREATE TABLE goal (
    id VARCHAR(40) PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    unit_id VARCHAR(40) NOT NULL REFERENCES org_unit(id),
    owner_name VARCHAR(255),
    parent_goal_id VARCHAR(40) REFERENCES goal(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','DRAFT','COMPLETED','CANCELLED')),
    due_date DATE NOT NULL,
    CHECK (parent_goal_id IS DISTINCT FROM id)
);
CREATE INDEX idx_goal_unit ON goal(unit_id);
CREATE TABLE key_result (
    id VARCHAR(40) PRIMARY KEY,
    goal_id VARCHAR(40) NOT NULL REFERENCES goal(id),
    target_value NUMERIC(20,4),
    actual_value NUMERIC(20,4),
    weight NUMERIC(12,6) NOT NULL CHECK (weight >= 0 AND weight <= 100),
    fact_updated_at DATE
);
CREATE INDEX idx_kr_goal ON key_result(goal_id);
CREATE TABLE kkd_rule (
    code VARCHAR(50) PRIMARY KEY,
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('critical','major','minor')),
    title VARCHAR(255) NOT NULL,
    recommendation TEXT NOT NULL
);
CREATE TABLE kkd_run (
    id VARCHAR(60) PRIMARY KEY,
    snapshot_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('RUNNING','COMPLETED','FAILED')),
    compared_to VARCHAR(60) REFERENCES kkd_run(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (compared_to IS DISTINCT FROM id)
);
CREATE INDEX idx_run_date ON kkd_run(snapshot_date DESC);
CREATE TABLE run_unit (
    run_id VARCHAR(60) NOT NULL REFERENCES kkd_run(id) ON DELETE CASCADE,
    id VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_id VARCHAR(40),
    PRIMARY KEY (run_id, id)
);
CREATE TABLE run_goal (
    run_id VARCHAR(60) NOT NULL REFERENCES kkd_run(id) ON DELETE CASCADE,
    id VARCHAR(40) NOT NULL,
    unit_id VARCHAR(40) NOT NULL,
    owner_name VARCHAR(255),
    parent_goal_id VARCHAR(40),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE','DRAFT','COMPLETED','CANCELLED')),
    due_date DATE NOT NULL,
    PRIMARY KEY (run_id, id),
    FOREIGN KEY (run_id, unit_id) REFERENCES run_unit(run_id, id)
);
CREATE TABLE run_key_result (
    run_id VARCHAR(60) NOT NULL REFERENCES kkd_run(id) ON DELETE CASCADE,
    id VARCHAR(40) NOT NULL,
    goal_id VARCHAR(40) NOT NULL,
    target_value NUMERIC(20,4),
    actual_value NUMERIC(20,4),
    weight NUMERIC(12,6) NOT NULL,
    fact_updated_at DATE,
    PRIMARY KEY (run_id, id),
    FOREIGN KEY (run_id, goal_id) REFERENCES run_goal(run_id, id)
);
CREATE TABLE kkd_issue (
    id VARCHAR(36) PRIMARY KEY,
    run_id VARCHAR(60) NOT NULL REFERENCES kkd_run(id) ON DELETE CASCADE,
    rule_code VARCHAR(50) NOT NULL REFERENCES kkd_rule(code),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('critical','major','minor')),
    object_type VARCHAR(10) NOT NULL CHECK (object_type IN ('GOAL','KR')),
    object_id VARCHAR(40) NOT NULL,
    goal_id VARCHAR(40) NOT NULL,
    unit_id VARCHAR(40) NOT NULL,
    message TEXT NOT NULL,
    actual_value TEXT,
    expected_value TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    UNIQUE (run_id, rule_code, object_type, object_id),
    FOREIGN KEY (run_id, goal_id) REFERENCES run_goal(run_id, id),
    FOREIGN KEY (run_id, unit_id) REFERENCES run_unit(run_id, id)
);
CREATE INDEX idx_issue_run_unit ON kkd_issue(run_id, unit_id);
CREATE INDEX idx_issue_filters ON kkd_issue(run_id, rule_code, severity);

INSERT INTO kkd_rule (code, severity, title, recommendation) VALUES
('GOAL_NO_OWNER', 'critical', 'Цель без ответственного', 'Назначьте ответственного за цель.'),
('KR_WEIGHTS_SUM', 'major', 'Сумма весов КР не равна 100%', 'Укажите веса КР так, чтобы их сумма равнялась 100%.'),
('CASCADE_NO_PARENT', 'major', 'Нет связи с целью вышестоящего подразделения', 'Свяжите цель с целью одного из вышестоящих подразделений.'),
('KR_STALE_PROGRESS', 'minor', 'Факт КР устарел', 'Обновите фактическое значение и дату его обновления.');

--rollback DROP TABLE kkd_issue;
--rollback DROP TABLE run_key_result;
--rollback DROP TABLE run_goal;
--rollback DROP TABLE run_unit;
--rollback DROP TABLE kkd_run;
--rollback DROP TABLE kkd_rule;
--rollback DROP TABLE key_result;
--rollback DROP TABLE goal;
--rollback DROP TABLE org_unit;
