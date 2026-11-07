# REST API

Базовый адрес: `http://localhost:8080`. Кодировка UTF-8, JSON — snake_case.
Авторизация по условиям тестового задания не требуется.

## Прогоны

`GET /api/runs` → 200, `{ "items": [...] }`. Прогоны сортируются от новых к старым.
Поля: id, status (COMPLETED / RUNNING / FAILED), snapshot_date, compared_to
(объект id/snapshot_date или null), issues_count, created_at.
Для незавершённого/ошибочного прогона issues_count=null: частичные результаты не выдаются.
[Пример ответа](example-runs.json).

## Сводка

`GET /api/kkd/summary?run_id=RUN-002&unit_id=HQ` → 200.
Оба параметра обязательны. unit_id охватывает подразделение и всех потомков на дату снимка.
[Пример сводки](example-summary.json).

| Поле | Значение |
|---|---|
| run | id и snapshot_date текущего прогона |
| compared_to | id и дата предыдущего успешного, null у первого |
| unit | выбранное подразделение: id, name, parent_id |
| metrics | три метрики: key, title, unit, value, previous, delta, trend, higher_is_better |
| issues_by_severity | все три уровня с current, previous, delta |
| delta | new, persisting, resolved |

`trend`: better, worse, unchanged; null при отсутствии сравнимых значений.
Дельта = value − previous. Знак дельты не определяет оценку: учитывается higher_is_better.
У первого прогона поле верхнего уровня `delta` равно `null`. Прошлые значения, дельты и направления изменения метрик тоже равны `null`.
Если в подразделении нет активных целей, доля корректных целей равна `null`, количество проблем и затронутых целей равно 0.
Если подразделение отсутствует в запрошенном снимке, ответ — 404. Если оно появилось после предыдущего прогона, прошлые значения метрик и разбивки по важности равны `null`.

## Проблемы

`GET /api/kkd/issues?run_id=RUN-002&unit_id=CIB&severity=major&limit=20&offset=0` → 200.

| Параметр | Обязателен | Допустимые значения / поведение |
|---|---|---|
| run_id | да | существующий завершённый прогон |
| unit_id | нет | подразделение и потомки; без параметра вся организация |
| rule_code | нет | один из четырёх кодов GET /api/meta |
| severity | нет | critical, major, minor |
| delta_status | нет | new, persisting, resolved |
| limit | нет | 1..100, по умолчанию 20 |
| offset | нет | целое >=0, по умолчанию 0 |

Значения limit и offset записываются десятичными цифрами. Ответ содержит items, total, limit, offset. total — число проблем после фильтрации до пагинации.
Порядок стабилен: critical → major → minor, затем rule_code, object_id, id.
Если offset >= total, items=[], total сохраняется.
[Пример ответа](example-issues.json).

Статус изменения определяется по полным результатам двух прогонов. Фильтр подразделения применяется по текущему снимку для new/persisting и по предыдущему для resolved.

Без delta_status возвращаются текущие проблемы (new и persisting, а у первого прогона
с delta_status=null). `delta_status=resolved` возвращает проблемы предыдущего прогона,
которых нет в выбранном. Поэтому у исправленной проблемы run_id и id относятся к
предыдущему прогону, а delta_status равен resolved. У первого прогона фильтры
new/persisting/resolved дают пустой список: сравнения ещё нет.

`GET /api/kkd/issues/{id}` → 200: id, run_id, rule_code, severity, object_type,
object_id, goal_id, unit_id, description, actual_value, expected_value, recommendation,
delta_status. Актуальность дельты у этого метода определяется относительно собственного
run_id проблемы. Для resolved используйте контекст списка выбранного прогона.

## Справочники

`GET /api/meta` → 200: units (id/name/parent_id), rules (code/severity/title/recommendation),
severities. Возвращает текущую организационную структуру. Историческая сводка использует
структуру из своего снимка.

## Создание прогона

`POST /api/runs`, `Content-Type: application/json`:

```json
{"snapshot_date":"2026-11-09"}
```

Ответ 201 — объект прогона того же формата, что элемент GET /api/runs.
Проверка синхронная: ответ содержит завершённый результат. Поле snapshot_date обязательно. Оно принимает строку YYYY-MM-DD с календарной датой, которая должна быть строго позже даты последнего успешного прогона. При сравнении ошибочные
и выполняющиеся прогоны пропускаются. Новый прогон проверяет текущие goal/key_result,
сохраняет их копию и никогда не изменяет прошлые снимки.

```bash
curl -X POST 'http://localhost:8080/api/runs' \
  -H 'Content-Type: application/json' \
  -d '{"snapshot_date":"2026-11-09"}'
```

## Ошибки

```json
{"error":{"code":"VALIDATION_ERROR","message":"Обязательный параметр: run_id"}}
```

| HTTP | code | Условие |
|---|---|---|
| 404 | RUN_NOT_FOUND | неизвестный run_id |
| 404 | UNIT_NOT_FOUND | неизвестный unit_id |
| 404 | ISSUE_NOT_FOUND | неизвестный id проблемы |
| 422 | VALIDATION_ERROR | отсутствующее/пустое поле, неверная дата/тип/фильтр/диапазон, повторный или неизвестный query-параметр, неизвестное поле JSON |
| 409 | RUN_FAILED | попытка прочитать результаты FAILED |
| 409 | RUN_IN_PROGRESS | попытка прочитать результаты RUNNING |
| 404 | ENDPOINT_NOT_FOUND | неизвестный маршрут |
| 405 | METHOD_NOT_ALLOWED | неверный HTTP-метод |
| 415 | UNSUPPORTED_MEDIA_TYPE | неверный Content-Type тела |
| 406 | NOT_ACCEPTABLE | несовместимый Accept |
| 500 | INTERNAL_ERROR | непредвиденная ошибка, детали остаются в серверном журнале |

Прогон с ошибкой и выполняющийся прогон видны в GET /api/runs, но их summary/issues
возвращают 409. В демонстрационных данных ровно два COMPLETED; остальные статусы
покрыты интеграционными тестами.
