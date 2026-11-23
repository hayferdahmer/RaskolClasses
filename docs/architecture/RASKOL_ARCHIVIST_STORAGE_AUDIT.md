# RASKOL Archivist — SafeStorage audit

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Целевая платформа: Paper 26.2 / Java 25
- Базовая ветка: `main`
- Этап: source-backed audit; production storage code не изменялся
- Владелец следующего этапа: RASKOL Archivist

## Краткий вывод

Обнаружен P1-риск потери данных при полном отказе восстановления YAML:

1. `SafeStorage.loadWithFallback` проверяет основной файл, затем `.tmp`, затем `.bak`.
2. Если основной файл и обе резервные копии отсутствуют/невалидны, метод возвращает пустой `YamlConfiguration`.
3. Вызывающий storage-класс не получает признака аварийного восстановления и может продолжить работу как с новым хранилищем.
4. Последующий `saveAtomic` записывает новый snapshot поверх основного файла; при этом прежний повреждённый файл может быть скопирован в `.bak`.
5. Исходный повреждённый YAML не помещается в карантин, а причина/путь восстановления не представлены вызывающему коду как типизированное состояние.

Это противоречит политике RASKOL: повреждённые пользовательские данные нельзя молча заменять пустым состоянием. Автоматически удалять или переписывать player data, неизвестные файлы и повреждённые исходники запрещено.

## Подтверждённые точки использования

Поиск по исходникам основной ветки обнаружил следующие production call sites:

| Компонент | Файл | Поведение / риск |
|---|---|---|
| Spec2 migration | `spec/storage/Spec2StorageMigration.java` | Использует fallback как для нового, так и для существующего target; миграция должна различать отсутствие данных и повреждение данных |
| PlusStorage | `spec/build/PlusStorage.java` | Загружает подписки и gift-note; пустой fallback может потерять состояние подписок/уведомлений |
| HpBarService | `hud/HpBarService.java` | Загружает health persistence; пустой fallback скрывает потерю сохранённого состояния |
| CooldownManager | `ability/CooldownManager.java` | Загружает cooldown state; нужен явный degraded/recovery result |
| FoliantService | `foliant/FoliantService.java` | Загружает foliant locks; пустой fallback может снять сохранённые блокировки |
| ResourceService | `resource/ResourceService.java` | Загружает ресурсы и pending resets; пустой fallback может потерять pending операции |
| SalvageDailyLimitStore | `item/craft/SalvageDailyLimitStore.java` | Загружает дневные лимиты и pending reservations; пустой fallback может сбросить anti-abuse state |
| CharacterProfileStorage | `character/profile/CharacterProfileStorage.java` | Загружает player profiles и schema version; особенно чувствительные player data |
| SelftestRunner | `selftest/SelftestRunner.java` | Использует SafeStorage для диагностического self-test; должен продолжить тестировать recovery отдельно от production semantics |

Поиск `AsyncStorageWriter` также подтвердил shared writer ownership в `RaskolClasses` и несколько потребителей. Его нельзя закрывать из отдельных storage-компонентов: общий writer закрывается root lifecycle последним.

## SafeStorage — конкретные замечания

### P1 — fail-open после отказа всех копий

Текущий контракт `loadWithFallback(File, Logger)` возвращает пустую конфигурацию при невозможности прочитать main/tmp/backup. Это смешивает два принципиально разных состояния:

- новое хранилище: все три файла отсутствуют;
- повреждённое существующее хранилище: хотя бы один файл существует, но ни одна копия не читается.

Для второго состояния пустой YAML недопустим.

### P1 — отсутствие quarantine / сохранения доказательств

Неудачные main/tmp/backup остаются на месте без единого recovery record. При последующем сохранении повреждённый main может попасть в backup, а новый snapshot — заменить его. Перед любым восстановительным действием исходные байты и метаданные должны быть сохранены без перезаписи.

### P2 — слишком широкий fallback после AtomicMove

`saveAtomic` перехватывает любой `IOException` от `ATOMIC_MOVE` и пытается обычный replace. Обычный fallback оправдан только для явно неподдерживаемого atomic move. Ошибки прав доступа, I/O и файловой системы нельзя маскировать повторной операцией с менее сильными гарантиями.

### P2 — null parent path

Путь, созданный как относительный файл без parent directory, может привести к `NullPointerException` при построении sidecar paths. Метод должен нормализовать target path/parent directory до любых операций.

### P2 — отсутствие валидации snapshot до замены

Сейчас `saveAtomic(String, ...)` записывает произвольную строку как YAML без проверки, что она парсится и соответствует ожидаемому формату. Проверка сериализованного snapshot до commit должна быть частью безопасной записи.

## AsyncStorageWriter — подтверждённый контракт

- Один FIFO worker и ограниченная очередь — корректная базовая модель последовательной записи.
- Completion callback может выполняться на storage worker, на submitting thread при отказе постановки, либо на shutdown thread при отмене queued task.
- После timeout `shutdownNow` отменяет queued tasks; уже исполняющаяся операция может продолжить работу.
- Writer не должен обращаться к Bukkit API.
- Shared writer должен закрываться только владельцем plugin lifecycle после финальных snapshots.

Это уже частично задокументировано в `docs/architecture/A04_ASYNC_COMPLETION_OWNERSHIP_AUDIT.md`; текущий этап не должен дублировать этот аудит или менять callback semantics без отдельного обоснования.

## DirtyStorageState

- Использует обычные `HashSet` и примитивные поля; thread-safe контракт отсутствует.
- По текущей реализации это main-thread/domain state, а не объект для worker-thread mutation.
- `clearIfUnchanged(generation)` обеспечивает защиту от очистки dirty-флага при появлении нового snapshot.
- В следующем этапе нужно проверить все вызовы и зафиксировать thread ownership. Не переводить коллекции на concurrent без доказанного требования.

## Целевой безопасный контракт загрузки

Следующая реализация должна различать:

1. `NEW` — main/tmp/backup отсутствуют; разрешено создать пустое хранилище.
2. `LOADED_MAIN` — успешно загружен основной файл.
3. `RECOVERED_TMP` — восстановлено из валидного tmp; требуется явный recovery status и безопасная фиксация.
4. `RECOVERED_BACKUP` — восстановлено из валидного backup; требуется явный recovery status и безопасная фиксация.
5. `CORRUPT` — файлы существовали, но ни одна копия невалидна; запуск соответствующего storage owner должен завершиться fail-closed, а данные — сохраниться для ручной диагностики.
6. `IO_ERROR` — чтение невозможно из-за I/O/permissions; это не должно трактоваться как новый файл или YAML corruption.

Предпочтительный API: типизированный результат загрузки (например, `StorageLoadResult`) с конфигурацией, состоянием источника и диагностикой. Не возвращать пустой YAML для состояний CORRUPT/IO_ERROR.

## Quarantine policy

- До любых перемещений/переименований создать неизменяемую копию повреждённого файла с уникальным именем и metadata sidecar (original path, timestamp, size, SHA-256, failure reason).
- Quarantine не удаляет оригинал автоматически.
- При невозможности создать и проверить quarantine copy — запретить последующую запись в target.
- Никакого автоматического GC quarantine на первом этапе.
- Не архивировать активные YAML.
- Не очищать неизвестные файлы и player data автоматически.

## План реализации после аудита

1. Добавить тесты на различие NEW / CORRUPT / IO_ERROR, включая сценарий «main + tmp + bak повреждены → следующий save не должен уничтожать ни один исходник».
2. Ввести типизированный результат загрузки и обновить все перечисленные call sites; отдельно проверить migration flow.
3. Реализовать quarantine copy + hash verification с fail-closed поведением.
4. Разделить `AtomicMoveNotSupportedException` от прочих I/O ошибок.
5. Валидировать snapshot до commit и проверить null-parent/relative path.
6. Проверить writer queue saturation, timeout, interruption, active write during shutdown и отсутствие потерянных futures.
7. Добавить Archivist report: состояние каждого хранилища, источник восстановления, hash, quarantine path, pending writes и итоговый статус. Диагностика не должна выводить содержимое player data.
8. После unit/integration tests выполнить Paper 26.2 runtime QA и только затем включать maintenance/GC политики.

## Тестовые требования перед merge

- Новый storage (нет main/tmp/backup) стартует пустым и создаётся при первом save.
- Валидный main побеждает tmp/backup.
- Невалидный main восстанавливается из валидного tmp; если tmp невалиден — из валидного backup.
- Если все существующие копии невалидны, load возвращает CORRUPT, не пустой YAML.
- После CORRUPT любой save блокируется; исходные файлы byte-for-byte сохранены.
- Quarantine copy проверяется по SHA-256 до любого дальнейшего действия.
- Ошибка чтения не маскируется под отсутствие файла.
- Ошибка atomic move, отличная от unsupported, не запускает обычный replace.
- Ошибка записи/валидации не очищает dirty state и не подтверждает snapshot.
- Timeout shutdown не оставляет queued futures незавершёнными.
- Все production call sites корректно обрабатывают recovery/fail-closed result.

## Границы текущего PR

В этом audit-only PR не меняются YAML schema, storage runtime, миграции, player data и lifecycle. Реализация будет отдельным следующим PR после того, как тестовый контракт зафиксирован и call sites инвентаризированы.
