# Дизайн

## Слои

- `domain`: неизменяемые данные, статусы, ключ идентичности проблемы.
- `rule`: четыре независимых правила и RuleEngine; чистая логика без SQL/HTTP.
- `repository`: JDBC, чтение и сохранение снимков, прогонов, проблем.
- `service`: создание прогонов, метрики, сравнение, фильтрация и демонстрационные данные.
- `api`: REST DTO, валидация и единый обработчик ошибок.

## Таблицы

| Таблица | Назначение / ключ |
|---|---|
| org_unit | id, name, parent_id → org_unit |
| goal | id, unit_id, owner_name, parent_goal_id → goal, status, due_date |
| key_result | id, goal_id, target_value, actual_value, weight, fact_updated_at |
| kkd_rule | code, severity, title, recommendation |
| kkd_run | id, snapshot_date, status, compared_to → kkd_run, created_at |
| run_unit | (run_id, id), name, parent_id |
| run_goal | (run_id, id), unit_id, owner_name, parent_goal_id, status, due_date |
| run_key_result | (run_id, id), goal_id, target/actual/weight/fact_updated_at |
| kkd_issue | id, run_id, rule_code, severity, object_type, object_id, goal_id, unit_id, описание |

Снимки копируют входные данные: исправление живой цели, смена её подразделения или
иерархии не меняет прошлый результат. Нет FK из снимков в живые таблицы. Идентификатор
проблемы детерминирован из run_id и естественного ключа; UNIQUE запрещает дубли внутри прогона.
Справочник правил хранится в БД. Java-компоненты задают условия проверок. Важность проблемы записывается при создании прогона и читается из его результатов.

## Правила

Активная цель имеет только статус ACTIVE. DRAFT, COMPLETED, CANCELLED исключены.

- GOAL_NO_OWNER: owner_name = null или String.isBlank(). Объект GOAL.
- KR_WEIGHTS_SUM: точная BigDecimal-сумма весов всех КР != 100, без округления.
  Отсутствие КР даёт сумму 0 и тоже нарушение. Объект GOAL, одна проблема на цель.
- CASCADE_NO_PARENT: цели корневого подразделения освобождены. У остальных родительская
  цель должна существовать и принадлежать строгому предку подразделения. Связь с целью
  своего подразделения, соседа или потомка не подходит. Статус родительской цели не влияет.
  Объект GOAL. Обход иерархии защищён от циклов.
- KR_STALE_PROGRESS: fact_updated_at = null или дата обновления < snapshot_date - 45 дней.
  Ровно 45 дней допустимо. Будущая дата не считается устаревшей. Объект KR.

Срок цели, target и actual хранятся, но не участвуют в четырёх выбранных правилах.

## Метрики

N — количество ACTIVE в выбранном подразделении и всех его потомках на дату снимка.
B — число различных активных целей с хотя бы одной проблемой. I — число проблем.

| key | Формула | Лучше |
|---|---|---|
| clean_goals_share | (N - B) / N × 100, HALF_UP до 1 знака | больше |
| issues_count | I | меньше |
| affected_goals_count | B | меньше |

При N=0 доля корректных целей равна null, I и B равны 0. Дельта вычисляется из отображаемых значений,
чтобы совпадать с числами в ответе. Если любой операнд null, delta/trend = null.
trend = better / worse / unchanged, с учётом higher_is_better.
Количество по важностям для пустого набора равно 0.

## Сравнение

compared_to — последний COMPLETED с более ранней snapshot_date. FAILED/RUNNING пропускаются.
Ссылка сохраняется при создании и не пересчитывается при чтении.
Ключ проблемы: (rule_code, object_type, object_id), значения/текст/важность в идентичность не входят.
new = current − previous, persisting = пересечение, resolved = previous − current.
У первого прогона compared_to и всё поле delta равны null. Проблемы имеют delta_status=null.
В каждом прогоне область подразделения определяется собственной сохранённой иерархией.
Статус проблемы определяется до фильтрации по подразделению. Сохраняющаяся проблема переносится вместе с объектом в его текущее подразделение. Исправленные проблемы относятся к подразделению предыдущего снимка.
Подразделение должно присутствовать в запрошенном снимке. Если его ещё не было в прошлом снимке, поля previous/delta у метрик и уровней важности равны null.

## Миграции

У обоих SQL-файлов фиксирован logicalFilePath в master changelog: идентичность
changeset одинакова при запуске Spring Boot из JAR и Maven-плагина из исходников.
В Maven-плагине includeArtifact=false: ручное управление миграциями не пересоздаёт
исполняемый JAR как обычный архив классов. Это проверено запуском JAR после цикла отката.

Откат миграции демоданных удаляет её 48 КР, 24 цели и три подразделения. Снимки прогонов хранятся отдельно. Полный откат схемы удаляет таблицы модуля.

## Транзакции

Демонстрационная инициализация атомарна и повторно не выполняется при наличии прогонов.
Создание прогона сериализовано PostgreSQL advisory transaction lock. Исходные таблицы
читаются под SHARE-блокировкой: параллельная запись не может изменить данные между чтением
подразделений, целей и КР. Снимок, проблемы и COMPLETED сохраняются одной транзакцией.
Повторный старт контейнера не меняет снимки и не создаёт дополнительные прогоны.

## Контракт

JSON — snake_case. Примеры приведены в [описании API](../docs/api.md).

| Метод | Параметры | Результат |
|---|---|---|
| GET /api/runs | — | items: [{id,status,snapshot_date,compared_to,issues_count,created_at}] |
| GET /api/kkd/summary | run_id, unit_id обязательны | run, compared_to, unit, metrics, issues_by_severity, delta |
| GET /api/kkd/issues | run_id обязателен; unit_id, rule_code, severity, delta_status; limit=20 (1..100), offset=0 (>=0) | items, total, limit, offset |
| GET /api/kkd/issues/{id} | id | подробности проблемы |
| GET /api/meta | — | units, rules, severities |
| POST /api/runs | {snapshot_date: YYYY-MM-DD} | 201, созданный прогон |

Ошибки: 404 RUN_NOT_FOUND / UNIT_NOT_FOUND / ISSUE_NOT_FOUND; 422 VALIDATION_ERROR;
409 RUN_FAILED / RUN_IN_PROGRESS. Неверные типы, даты, пустые/повторные/неизвестные
query-параметры и неизвестные поля JSON отклоняются. HTTP 405/415/500 также имеют единый envelope.

Демо: RUN-001 (2026-10-26), RUN-002 (2026-11-02), 24 цели / 21 ACTIVE, HQ → CIB и SME.
Первый прогон: 12 проблем; второй: 11, new=4, persisting=7, resolved=5.
