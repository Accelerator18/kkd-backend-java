# Соответствие тестовому Backend Java

| Требование | Реализация | Проверка |
|---|---|---|
| Java 17+, Spring Boot 3, Maven | pom.xml, KkdApplication | Maven verify |
| PostgreSQL 15+ | PostgreSQL 16 в docker-compose.yml, JDBC | PostgresIT |
| Иерархия подразделений, цели, КР | 001-schema.sql | MigrationIT, PostgresIT |
| Справочник правил, прогон, проблема | kkd_rule, kkd_run, kkd_issue | MigrationIT, PostgresIT |
| Четыре правила из списка | rule/ | RulesTest: границы, неактивные цели, каскадирование |
| Логика отдельно от API и БД | KkdRule, RuleEngine | Прямые вызовы в unit-тестах |
| 20–30 целей, 2–3 подразделения, два прогона | 24 цели, 3 подразделения, RUN-001 и RUN-002 | PostgresIT |
| Нарушения каждого правила, данные для сравнения | По каждому правилу проблемы есть в обоих прогонах | PostgresIT, example-summary.json |
| Liquibase/Flyway с откатом | Liquibase, явный rollback обоих changeset | MigrationIT: update → rollback(2) → update |
| Запуск docker compose up | Dockerfile + docker-compose.yml | docker compose config; для запуска нужен Docker Engine |
| GET /api/runs | KkdController.runs | PostgresIT |
| Summary: 3 метрики, previous, delta, trend | MetricCalculator, KkdQueryService.summary | MetricCalculatorTest, PostgresIT |
| Метрики включают потомков | UnitScope и снимки иерархии | MetricCalculatorTest, PostgresIT |
| Разбивка по важностям, new/persisting/resolved | KkdQueryService, DeltaCalculator | DeltaCalculatorTest, PostgresIT |
| Список проблем с фильтрами и пагинацией | KkdQueryService.issues | PostgresIT |
| Идентичность по правилу и объекту | IssueKey | DeltaCalculatorTest |
| Первый прогон: null вместо нулевого сравнения | ApiModels, MetricCalculator, DeltaCalculator | Unit-тесты и PostgresIT |
| 404 / 422 и единый JSON ошибки | ApiExceptionHandler, QueryValidationConfig | PostgresIT |
| JUnit 5: минимум 4 сценария | RulesTest, MetricCalculatorTest, DeltaCalculatorTest | 30 unit-тестов |
| Настройки из окружения, .env.example | application.yml, docker-compose.yml | Запуск собранного JAR с DB_URL/DB_USER/DB_PASSWORD |
| Инструкция запуска, тестов, отката, архитектура | README.md | Команды Maven и цикл миграций выполнены |
| Мотивация участия | docs/motivation.md | Мотивация, роль в команде и ориентир занятости |
