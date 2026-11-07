--liquibase formatted sql
--changeset kkd:002-demo-data
INSERT INTO org_unit (id, name, parent_id) VALUES
('HQ', 'Дирекция B2B+', NULL),
('CIB', 'Корпоративный бизнес', 'HQ'),
('SME', 'Малый и средний бизнес', 'HQ');

INSERT INTO goal (id, title, unit_id, owner_name, parent_goal_id, status, due_date)
SELECT 'G-' || lpad(i::text, 3, '0'), 'Цель ' || i,
       CASE WHEN i <= 8 THEN 'HQ' WHEN i <= 16 THEN 'CIB' ELSE 'SME' END,
       CASE WHEN i IN (3, 19) THEN NULL WHEN i = 11 THEN '   ' ELSE 'Сотрудник ' || i END,
       CASE WHEN i <= 8 OR i IN (9,17) THEN NULL
            WHEN i = 10 THEN 'G-009' ELSE 'G-001' END,
       CASE WHEN i = 8 THEN 'COMPLETED' WHEN i = 16 THEN 'DRAFT'
            WHEN i = 24 THEN 'CANCELLED' ELSE 'ACTIVE' END,
       DATE '2026-12-31'
FROM generate_series(1,24) AS i;

INSERT INTO key_result (id, goal_id, target_value, actual_value, weight, fact_updated_at)
SELECT 'KR-' || lpad(i::text, 3, '0') || '-' || k, 'G-' || lpad(i::text, 3, '0'),
       100, 30,
       CASE WHEN i = 4 THEN CASE WHEN k = 1 THEN 70 ELSE 20 END
            WHEN i = 12 THEN 40 WHEN i = 20 THEN 30 ELSE 50 END,
       CASE WHEN i = 2 AND k = 1 THEN DATE '2026-09-10'
            WHEN i = 13 AND k = 1 THEN DATE '2026-09-06'
            WHEN i = 22 AND k = 1 THEN NULL ELSE DATE '2026-10-20' END
FROM generate_series(1,24) AS i CROSS JOIN generate_series(1,2) AS k;

--rollback DELETE FROM key_result WHERE id IN (SELECT 'KR-' || lpad(i::text, 3, '0') || '-' || k FROM generate_series(1,24) AS i CROSS JOIN generate_series(1,2) AS k);
--rollback DELETE FROM goal WHERE id IN (SELECT 'G-' || lpad(i::text, 3, '0') FROM generate_series(1,24) AS i);
--rollback DELETE FROM org_unit WHERE id IN ('HQ','CIB','SME');
