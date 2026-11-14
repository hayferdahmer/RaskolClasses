# A-03-003.25 — Storage Completion Callback Ownership Audit

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Базовый HEAD аудита: `605ba83026a1c582f7b2349ba19f7c193a5a70a9`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed audit; на этом этапе production persistence behavior не меняется.
- Область: `AsyncStorageWriter`, `HpBarService`, `FoliantService`, `ResourceService`, `CooldownManager`, shutdown ordering.

## 1. Главный контракт AsyncStorageWriter

`AsyncStorageWriter.submit(file, snapshot)` принимает готовую immutable строку и выполняет файловую операцию в одном worker thread, сохраняя FIFO-порядок поставленных задач.

Completion callback `CompletableFuture` не имеет гарантированного executor:
- при штатном завершении callback `whenComplete` обычно исполняется непосредственно на storage worker thread;
- при отказе `executor.execute` задача завершается через `PendingWrite.finish(false)` внутри `submit`, поэтому callback может исполниться синхронно на вызывающем потоке;
- после `accepting=false` возвращается уже завершённый future со значением `false`, и callback также может исполниться inline на вызывающем потоке;
- при отмене queued writes в `closeAndAwait` future завершается потоком, выполняющим `cancelQueuedWrites()`.

Следствие: callback не должен считать себя main-thread callback и не должен обращаться к Bukkit API, Bukkit mutable configuration или доменному состоянию без явного переноса в нужный execution context.

`AsyncStorageWriter` сам не вызывает Bukkit API и не должен получать знание о плагине, игроках или доменах.

## 2. Подтверждённые call sites

| Сервис | Completion path | Контекст/подтверждение | Текущий вывод |
|---|---|---|---|
| `HpBarService.flushDirty()` | writer → `runTask(plugin, ...)` → `clearIfUnchanged(generation)` | Generation acknowledgement выполняется в Bukkit task; при scheduling failure dirty остаётся | Основной thread boundary корректен; отсутствует явный in-flight gate, возможны дублирующие snapshots |
| `FoliantService.saveLocks()` | writer → `runTask(plugin, ...)` → `clearIfUnchanged(generation)` | Generation acknowledgement выполняется в Bukkit task; failure оставляет dirty | Основной thread boundary корректен; несколько snapshots одного поколения возможны |
| `ResourceService.persistAsync()` | writer → `runOnMainThread` → completion consumer | Completion вызывается на main thread или планируется туда | Bukkit/state boundary в текущих consumers соблюдён; нет единого generation/token протокола для разных операций |
| `CooldownManager.persistAsync()` | writer → logger | Не обращается к Bukkit и mutable state | Callback безопасен по контексту; failure только логируется, повторная попытка зависит от следующего autosave |
| `AsyncStorageWriter` | future completion | Caller/worker зависит от исхода submit | Нужна явная документированная семантика callback thread; не следует добавлять неявный Bukkit dispatcher внутрь writer |

## 3. Сервисные выводы

### 3.1 HpBarService

- Снимок YAML и generation фиксируются до submit.
- Dirty очищается только после успешной записи и только если generation не изменилось.
- Новые изменения во время записи не теряются: acknowledgement старого generation не очищает более новое состояние; текущая реализация инициирует следующий flush.
- При невозможности поставить Bukkit acknowledgement dirty-маркеры остаются.
- Нет флага/handle in-flight write. Пока dirty остаётся true, очередные tick/flush могут ставить повторные snapshots одного поколения. Это лишняя нагрузка, не подтверждённая потеря данных.
- Shutdown вызывает `saveAll()` до `AsyncStorageWriter.closeAndAwait()`. Writer завершает дисковые операции, но main-thread acknowledgement может остаться в очереди и не исполниться во время disable. Это не отменяет уже завершённую запись, но не очищает in-memory dirty state; после disable он больше не нужен.

### 3.2 FoliantService

- `saveLocks()` сериализует создание снимка через synchronized method, фиксирует generation и очищает dirty только через main-thread acknowledgement после успешной записи.
- Новые изменения после снимка не очищаются старым acknowledgement.
- При scheduling failure acknowledgement не выполняется, dirty остаётся консервативно установленным.
- Периодический flush может поставить несколько одинаковых snapshots до завершения первого; отдельного in-flight gate нет.
- Root закрывает `FoliantRuntimeOwner`, затем вызывает `saveLocksIfDirty()`, затем закрывает общий writer последним.
- Возврат `saveLocks()` отражает только постановку/уже завершившийся результат submit, а не окончательное durable completion. Это различие нужно сохранить в API и документации.

### 3.3 ResourceService

- Snapshot создаётся на main thread; writer получает только строку и файл.
- Completion переносится через `runOnMainThread` перед выполнением переданного consumer.
- Reset marker операции имеют разные смыслы: `persistResetMarker`, join completion, quit completion и bulk `saveAll`.
- Bulk save удаляет pending marker из snapshot только для online players, удерживая UUID в `resetOnJoin` до успешной записи; при failure marker восстанавливается.
- Текущие consumers меняют `resetOnJoin`/YAML только после возврата на main thread.
- В протоколе нет общего per-player generation/token. Наложение разных операций для одного UUID требует отдельного тестирования, особенно успешное завершение старого snapshot после постановки нового reset marker.
- Нельзя механически заменить все consumers единым callback owner: reset marker completion является частью domain persistence protocol.

### 3.4 CooldownManager

- `persistAsync()` сериализует YAML на вызывающем потоке и отправляет строку writer.
- Completion только пишет лог; Bukkit API и runtime map не затрагиваются.
- Ошибка записи не очищает in-memory YAML store. Следующий autosave/disable может повторить снимок, но отдельного retry state/handle нет.
- `onPlayerQuit` обновляет in-memory store и удаляет runtime map; дисковая запись остаётся на autosave/final save по текущему контракту.
- Отдельная проблема notification BukkitTask map (удаление завершённых handles) относится к runtime task ownership, не к storage completion callback.

## 4. Shutdown ordering

Подтверждённый порядок root:
1. Закрываются periodic runtime owners, включая autosave и Foliant producer.
2. Foliant ставит финальный dirty snapshot.
3. Resources, HP Bar, Spec2 и Cooldowns ставят финальные snapshots.
4. Общий `AsyncStorageWriter.closeAndAwait(Duration.ofSeconds(10))` вызывается последним.

Это правильный принципиальный порядок: producers останавливаются до финальных snapshots, writer закрывается после всех submit calls.

Отдельное ограничение `closeAndAwait`: при timeout он вызывает `shutdownNow()`, завершает futures для queued writes значением false и возвращает false; уже выполняющаяся файловая операция может продолжить работу. Улучшение drain/timeout semantics относится к A-04 и не должно незаметно решаться в callback ownership slice.

## 5. Риски и приоритеты

### P1 — закрепить контракт execution context
Добавить тесты/документацию, подтверждающие:
- штатный completion может прийти с worker thread;
- rejected submission может завершить future inline на caller thread;
- callback adapters должны явно маршалить Bukkit/domain work на main thread;
- callback исключение не должно менять результат уже завершённой файловой операции.

### P1 — ResourceService per-player completion ordering
Добавить deterministic tests для последовательности:
- старый reset/quit/bulk snapshot submitted;
- новый reset marker устанавливается до completion старого snapshot;
- старый success/failure callback доставляется позже;
- новое намерение сброса не должно быть потеряно из in-memory state или следующего durable snapshot.

Если тест подтвердит потерю намерения, исправлять через per-player generation/token или эквивалентный versioned acknowledgement, а не через общий глобальный флаг.

### P2 — in-flight coalescing
Рассмотреть отдельный in-flight gate для HP Bar и Foliant:
- не ставить один и тот же snapshot повторно;
- после completion сравнить актуальную generation с snapshot generation;
- если состояние изменилось, поставить ровно один новый snapshot;
- failure сохраняет dirty и допускает следующую попытку.
Это оптимизация/упрощение очереди, не объявляется исправлением подтверждённой потери данных.

### P2 — shutdown completion observability
Зафиксировать, что shutdown ждёт файловые операции, а не Bukkit main-thread acknowledgements. Durable success должен определяться writer result, а in-memory acknowledgement после начала disable не является обязательным для уже закрываемого экземпляра плагина.

## 6. Решение по границам ответственности

- `AsyncStorageWriter` остаётся инфраструктурным последовательным writer и не получает Bukkit scheduler dependency.
- Не создавать универсальный `StorageCallbackRuntimeOwner`, который скрывает семантику dirty generation и reset markers.
- При необходимости ввести маленький execution-context port/adapter, но ownership generation, dirty state и retry остаётся у соответствующего storage/domain service.
- `HpBarService` и `FoliantService` сохраняют versioned acknowledgement.
- `ResourceService` сохраняет domain-specific reset protocol и получает отдельные race regression tests.
- `CooldownManager` сохраняет log-only completion, пока A-04 не определит централизованный retry/health contract.
- Storage queue draining, timeout, rejection metrics и durable failure policy остаются A-04.

## 7. Acceptance criteria для закрытия A-03-003.25

- [x] Source-backed inventory всех четырёх сервисов и writer.
- [x] Проверен completion thread contract для штатного, rejected и shutdown пути.
- [x] Проверен root shutdown ordering: producers → final snapshots → writer close.
- [ ] Добавлены deterministic tests на execution context/rejected completion.
- [ ] Добавлены ResourceService reset marker race tests.
- [ ] Зафиксировано решение о coalescing HP Bar/Foliant.
- [ ] Полный Build/Analyze/Spotless CI на одном HEAD.
- [ ] Inventory и roadmap синхронизированы.

До выполнения оставшихся критериев срез остаётся открытым.
