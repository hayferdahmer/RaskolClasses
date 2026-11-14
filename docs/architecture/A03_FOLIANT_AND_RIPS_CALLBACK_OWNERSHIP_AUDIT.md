# A-03-003.30 — Foliant transition and RIPS enforcement callback ownership audit

## Статус

- Репозиторий: hayferdahmer/RaskolClasses
- Ветка: refactor/a03-foundation-lifecycle
- Проверенный HEAD до аудита: f74e06ee0949f4481d62f07ce02067d9aad82075
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed lifecycle audit; production-код и тесты в этом срезе не менялись.
- Область: два next-tick/main-thread callback, ранее вынесенных из A-03-003.28.

## 1. FoliantService.executeTransition() — post-transition verification

Файл: `src/main/java/dev/raskol/classes/foliant/FoliantService.java`.

После успешной смены LuckPerms-группы и очистки зависимого состояния метод ставит `runTask(plugin, callback)`. Callback на следующем Paper tick повторно получает класс игрока через `plugin.getClassProvider().getClassOf(player)` и пишет severe-log, если результат не `WARLOCK`.

Подтверждённые свойства:

- Callback одноразовый и имеет задержку до следующего scheduler tick.
- Возвращённый `BukkitTask` не сохраняется; явной отмены, completion release или регистрации в `TaskScope` нет.
- Callback захватывает объект `Player`, а не только UUID.
- Нет проверки `player.isOnline()` перед post-transition lookup/logging.
- `FoliantRuntimeOwner` сейчас владеет регистрацией listener и периодическим dirty-flush через `TaskScope`; next-tick verification callback в его ownership не входит.
- Root закрывает `FoliantRuntimeOwner` при shutdown. Paper также отменяет задачи, зарегистрированные от имени отключаемого plugin.

Оценка:

- Это не доказанный unbounded task leak: задача одноразовая и живёт максимум до следующего scheduler tick.
- Но это незамкнутый ownership contract внутри архитектуры: у Foliant callback нет явного владельца и нет определённого поведения при quit между transition и callback.
- Риск относится прежде всего к корректности диагностики/доступа к player state после disconnect, а не к накоплению задач.

Рекомендация для отдельного implementation slice:

- Ввести явный Foliant-owned one-shot callback boundary либо использовать отдельный экземпляр уже существующего player callback owner; не расширять общий scheduler и не создавать универсальный callback registry без дополнительных потребителей.
- Зафиксировать поведение при quit: отмена callback или безопасный skip после проверки актуального online player по UUID.
- Сохранить next-tick timing, post-transition verification и severe diagnostic при online player.
- Добавить focused tests на completion, quit-before-execution, shutdown-before-execution и отсутствие stale callback после замены (если выбран keyed owner).

Решение о точном seam/owner оставить implementation design gate: текущий аудит не вводит новую абстракцию заранее.

## 2. RaskolClasses.handleRuntimeIntegrityFailure() — security enforcement dispatch

Файл: `src/main/java/dev/raskol/classes/RaskolClasses.java`.

После того как enforcement policy подтверждает необходимость disable, метод:
1. Защищает постановку от дублей через `runtimeIntegrityFailureScheduled.compareAndSet(false, true)`.
2. Ставит main-thread callback через `getServer().getScheduler().runTask(this, ...)`.
3. В callback проверяет `isEnabled()`, логирует состояние integrity failure и вызывает `PluginManager.disablePlugin(this)`.
4. Если постановка задачи бросает `RuntimeException`, сбрасывает atomic guard и пишет severe-log.

Подтверждённые свойства:

- Это одноразовая security-enforcement операция, а не периодическая задача.
- Возвращённый `BukkitTask` не сохраняется.
- `RuntimeIntegrityRuntimeOwner` владеет monitor/executor, но не Bukkit scheduler callback.
- `RuntimeIntegrityMonitor.close()` останавливает monitor, завершает pending futures и вызывает `executor.shutdownNow()`; он не отменяет уже поставленный Paper callback.
- Shutdown выполняется на main thread. Если плагин отключается по другой причине до выполнения callback, Paper plugin scheduler завершает plugin-owned tasks при disable. Если callback выполняется, он сам инициирует disable.
- STOPPED snapshot при закрытии monitor не вызывает failure handler: handler вызывается только для состояния FAILED.

Оценка:

- Незарегистрированный handle здесь не образует доказанного долгоживущего ресурса: callback одноразовый и имеет plugin-scoped scheduler lifecycle.
- Atomic guard исключает дублирующие enforcement callbacks; `isEnabled()` защищает от выполнения после фактического disable.
- Не следует бездумно добавлять callback в owner, который отменяет его при shutdown: это может смешать остановку monitor с отменой уже принятого security enforcement решения.
- На текущем source нет подтверждённого дефекта, требующего production-изменения в этом срезе.

Рекомендация:

- Оставить callback plugin-scoped и сохранить текущую семантику enforcement.
- Явно документировать, что его lifecycle делегирован Paper plugin scheduler, а не `RuntimeIntegrityRuntimeOwner`.
- Не вводить generic runtime owner и не добавлять cancellation path без отдельного требования безопасности/тестового сценария.

## 3. Границы среза

- `FoliantService.saveLocks()` completion acknowledgement также использует main-thread scheduler, но это storage completion callback и он остаётся в A-04; повторно в этот аудит не включён.
- Periodic Foliant dirty-flush продолжает принадлежать `FoliantRuntimeOwner`.
- Runtime integrity monitor/executor продолжает принадлежать `RuntimeIntegrityRuntimeOwner`.
- Paper automatic plugin-task cancellation рассматривается как внешний lifecycle fallback, а не замена явного ownership для долгоживущих или заменяемых задач.
- `main` не менялся; `BootstrapCoordinator` не активировался.
- Полный repository-wide lifecycle scan всех source files не объявляется завершённым.

## 4. Вывод

Из двух кандидатов только Foliant post-transition verification имеет обоснованный следующий implementation slice: сделать владение callback явным и определить поведение при player quit. Для RIPS enforcement callback достаточно зафиксировать намеренное plugin-scoped владение; текущий source не подтверждает необходимость его переносить в runtime owner.

## 5. Acceptance criteria

- [x] Проверен точный branch HEAD перед аудитом.
- [x] Прослежены постановка, выполнение и shutdown lifecycle Foliant next-tick callback.
- [x] Прослежены policy gate, duplicate guard, scheduling failure, execution guard и monitor shutdown для RIPS enforcement callback.
- [x] Storage completion acknowledgement исключён и оставлен в A-04.
- [x] Определён узкий Foliant implementation candidate без преждевременного generic abstraction.
- [x] Зафиксировано, почему RIPS callback не требует cancellation owner в текущем дизайне.
- [ ] Реализован Foliant callback ownership boundary в отдельном implementation slice.
- [ ] Добавлены focused tests для Foliant callback lifecycle.
- [ ] Полный repository-wide task/registration scan завершён отдельными срезами.
- [ ] CI на финальном implementation HEAD прошёл.
- [ ] Live Paper 26.2 runtime smoke test выполнен.
