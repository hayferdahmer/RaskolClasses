# A-03-003.32 — Installation handler task ownership audit

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Проверенный audit HEAD: `0e4fd5277ad56d60f7df102ceac334dc02413bc0`
- Реализация: A-03-003.33 (в работе; CI ещё не подтверждён)
- Платформа: Paper 26.2 / Java 25
- Область: внутренние repeating tasks, создаваемые installation handlers, и их shutdown path.

## 1. Обнаруженные task-bearing handlers

В каталоге `install/` среди handlers обнаружены два владельца собственных BukkitTask:

### PentagramHandler

- `place()` создаёт repeating redraw task с начальной задержкой 10 ticks и периодом 10 ticks.
- Handle сохраняется в `redrawTasks`, ключ — installation UUID.
- Callback проверяет наличие собственного handle и доступность мира; при невозможности продолжать сам отменяется и удаляет запись.
- `expire()` удаляет handle из map и вызывает `cancel()`.
- Игровой tick установки выполняется отдельно через `InstallationService`; redraw task — только визуальный producer.

Оценка: handle явно сохраняется и имеет штатный путь отмены. Задача ограничена временем жизни установки при нормальном service lifecycle.

### FrostRuneHandler

- `place()` запускает ambient через `FxService.startAmbient()`, затем создаёт собственный ring-particle task с initial delay 0 и периодом 20 ticks.
- Состояние руны хранит ambient UUID и ring BukkitTask в `RuneState`, индексированное installation UUID.
- Ring callback проверяет наличие состояния и доступность мира; при отсутствии состояния отменяет себя.
- `expire()` удаляет состояние, останавливает ambient через FxService, отменяет ring task и удаляет timed attribute modifier.
- FxService отдельно владеет ambient task и закрывает его в собственном shutdown.

Оценка: оба task handle имеют явных владельцев и обычный expiry path. Ambient не дублируется в FrostRuneHandler как BukkitTask: handler хранит только FX identifier, а cancellation делегируется FxService.

## 2. Общий lifecycle InstallationService

Подтверждённые пути очистки:

- Естественное истечение срока: `tick()` удаляет installation и вызывает handler `expire()`.
- Handler сообщает, что установка завершена: `tick()` удаляет запись и вызывает `expire()`.
- Удаление установок игрока: `removeAllOf(UUID)` удаляет запись и вызывает `expire()`.
- Периодическая очистка: `purgeStale()` удаляет просроченные записи и вызывает `expire()`.
- Shutdown: `shutdown()` обходит копию активных установок, вызывает `expire()` для каждого handler, затем очищает installations и cooldown maps.

Root shutdown закрывает `InstallationRuntimeOwner` до вызова `InstallationService.shutdown()`; тем самым прекращается общий sweep producer до финальной очистки handler-owned tasks.

## 3. Остаточный риск — failure isolation при массовой очистке

В `InstallationService.shutdown()` вызовы handler `expire()` выполняются последовательно без локальной изоляции исключений. Если один handler cleanup выбросит RuntimeException, цикл прервётся до обработки оставшихся установок и до `installations.clear()` / `placeCooldowns.clear()`.

Аналогичная последовательная семантика присутствует в `removeAllOf(UUID)`: исключение одного `expire()` прекращает очистку остальных установок этого игрока. В `purgeStale()` side effect `expire()` находится внутри `ConcurrentHashMap.entrySet().removeIf`; исключение может оборвать проход, оставив часть stale entries.

Это не доказанный текущий runtime failure и не означает, что BukkitTask сейчас теряется при штатном expiry. Это failure-isolation gap для batch cleanup: остальные независимые installation handlers могут не получить вызов `expire()` после одной ошибки.

## 4. Рекомендация

Следующий implementation slice должен быть узким и посвящён failure-isolated cleanup в `InstallationService`:

1. Продолжать попытки очистки всех установок после ошибки отдельного handler.
2. Сохранять информацию об ошибках для итогового диагностического сообщения/исключения.
3. Определить политику для записи, чей `expire()` завершился ошибкой: не терять возможность повторной очистки её handler-owned task.
4. Сохранить прежнюю игровую семантику порядка удаления, cooldown и expiry.
5. Добавить focused tests для нескольких установок, включая сценарий, где первый handler бросает, а следующие всё равно очищаются; отдельно проверить retry semantics и purge path.

Не вводить универсальный runtime owner для всех installation handlers: существующие handler-specific task handles уже имеют локальных владельцев. Не менять FrostRune/Pentagram scheduling без отдельного доказательства необходимости.

## 5. Границы

- В этом аудите production source и tests не менялись.
- FX ambient lifecycle остаётся в `FxService`; его shutdown ownership реализован ранее.
- Общий installation sweep остаётся в `InstallationRuntimeOwner`.
- Storage completion callbacks относятся к A-04 и здесь не рассматриваются.
- `BootstrapCoordinator` не активировался.
- `main` не изменялся.
- Это не полный repository-wide scheduled-task scan.

## 6. Acceptance criteria

- [x] Проверены все Java handlers в `install/` на создание Bukkit tasks.
- [x] Для PentagramHandler прослежены schedule, handle storage, self-stop и expiry cancellation.
- [x] Для FrostRuneHandler прослежены ambient delegation, ring task storage, self-stop и expiry cancellation.
- [x] Проверены service expiry, removeAllOf, purgeStale и shutdown call paths.
- [x] Проверен root ordering: InstallationRuntimeOwner закрывается до InstallationService.shutdown().
- [x] Выделен конкретный failure-isolation gap без заявления о доказанном task leak.
- [ ] Live Paper 26.2 runtime smoke test не выполнялся.


## 7. A-03-003.33 — Installation cleanup failure isolation

Реализация добавляет `InstallationCleanupCoordinator`, который обходит snapshot реестра, изолирует RuntimeException каждого handler и удаляет запись только после успешного `expire()` с identity-safe `Map.remove(id, expected)`. Ошибки логируются с operation, installation id, type и owner; неудачные записи остаются в реестре.

- `shutdown()`, `removeAllOf()` и `purgeStale()` используют общий координатор; cooldown cleanup выполняется после обхода независимо от ошибок отдельных installations.
- `sweep()` использует тот же путь для expiry/triggered cleanup. Записи с неудачной очисткой помечаются pending и при следующем sweep повторно очищаются до очередного gameplay tick, чтобы не повторять эффект установки.
- `FrostRuneHandler.expire()` теперь выполняет независимые cleanup steps с per-step completion flags; состояние удаляется только после полного успеха.
- `PentagramHandler.expire()` сохраняет redraw handle при неудачной отмене и отдельно запоминает завершение визуального эффекта, чтобы повтор не дублировал уже успешную часть cleanup.
- Добавлен `InstallationCleanupCoordinatorTest`: изоляция нескольких установок, retry после transient failure, identity-safe removal и фильтрация по владельцу.

### Проверка

- [ ] Targeted JUnit tests
- [ ] Полный `mvn -Pdev -B verify` на итоговом HEAD
- [ ] Build и Analyze CI на одном итоговом HEAD
- [ ] Spotless Format на итоговом HEAD
- [ ] Live Paper 26.2 runtime smoke test не выполнялся

### Ограничения

Координатор изолирует `RuntimeException`, не `Error`. Retry cleanup в runtime выполняется на следующем installation sweep; при shutdown выполняется полный best-effort проход, после которого Paper самостоятельно отменяет plugin-owned tasks при disable. Изменения не затрагивают scheduling cadence, игровые эффекты tick, cooldown policy, `main` и `BootstrapCoordinator`.
