# A-03-003.36 — Lifecycle ownership residual audit

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Проверенный source HEAD: `af25876da973a60ec5db31bdcad5f37866654b6c`
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed residual audit; production-код и тесты не менялись.
- `main` на момент аудита: `2e407cbe59276e6af88857bf3a0d93595c55c52c`.

Аудит сверяет уже выделенные runtime owners, известные callback-и и оставшиеся shutdown boundaries. Он не объявляет завершённым полный repository-wide scan всех Java source: критерий полного сканирования остаётся открытым.

## 1. Результаты

### P1 — Root shutdown не полностью failure-isolated

Файл: `src/main/java/dev/raskol/classes/RaskolClasses.java`, `shutdownRuntime()`.

Большинство runtime owners закрываются в отдельных `try/catch`, но ряд последующих операций остаётся без независимой изоляции. Среди подтверждённых примеров:

- `runtimeIntegrityRuntimeOwner.close()`;
- `blueprintHook.unregisterBlueprints()`;
- `items.stop()`;
- `passportListener.unregister()`;
- финальные `resources.saveAll()`, `hpBarService.saveAll()`, `spec2Storage.save()`;
- `installations.shutdown()`;
- `classProvider.shutdown()` и `effects.clear()`.

Если одна из этих операций выбросит `RuntimeException`, выполнение `shutdownRuntime()` может оборваться до последующих cleanup steps, включая закрытие общего `AsyncStorageWriter`. Это не доказывает, что сбой происходит в штатной работе, но означает, что failure isolation сейчас локальная, а не сквозная.

**Рекомендация:** следующий implementation slice — `A-03-003.37 Root Shutdown Failure Isolation`. Он должен изолировать независимые cleanup steps, продолжать best-effort shutdown после отдельного сбоя и в конце агрегировать диагностику. При этом нельзя менять dependency ordering:
- остановить producers до финальных snapshots;
- закрыть InstallationRuntimeOwner и выполнить InstallationService cleanup, пока FxService доступен;
- закрыть FxService после installation cleanup;
- сохранить порядок финальных snapshots и закрыть общий StorageWriter последним.

Не следует бездумно оборачивать весь метод одним catch: это скроет место отказа и не гарантирует выполнение следующих шагов. Необходимо сохранить retry/повторную очистку там, где owner оставляет handles после неудачной отмены. Отдельно решить, какие ошибки shutdown должны быть только залогированы, а какие должны влиять на итоговый статус.

### P2 — TrailListener создаёт ограниченные, но неявно принадлежащие задачи

Файл: `src/main/java/dev/raskol/classes/fx/TrailListener.java`.

Каждый подходящий ProjectileLaunchEvent создаёт repeating BukkitRunnable с периодом 4 ticks. Задача самостоятельно отменяется при смерти/невалидности снаряда, отсутствии мира или после превышения 200 ticks. Handle не сохраняется в реестре listener-а и нет явного `close()`.

Оценка:
- это ограниченная по времени задача, а не доказанная unbounded leak;
- Paper отменяет plugin-owned scheduler tasks при disable;
- при штатном ходе задачи она self-cancels;
- явный owner улучшил бы тестируемость и возможность досрочной очистки, но добавил бы registry/quit/shutdown machinery для короткоживущих визуальных задач.

**Рекомендация:** не включать в ближайший P1 implementation slice. Оставить как осознанное исключение либо вернуться к нему при общем FX/listener lifecycle pass. Не смешивать TrailListener с FxService: его projectile trails не используют FxService task registry.

### P2 — HP respawn callbacks индексируются по UUID, но захватывают Player

Файлы:
- `src/main/java/dev/raskol/classes/attribute/HpAttributeSync.java`;
- `src/main/java/dev/raskol/classes/hud/HpBarService.java`;
- `src/main/java/dev/raskol/classes/bootstrap/runtime/PlayerCallbackRuntimeOwner.java`.

Оба respawn handler-а передают в UUID-keyed owner lambda, которая замыкает исходный `Player`. Задержки ограничены: 2 ticks для HP attribute sync и 0 ticks для HP bar. Owner умеет отменять callback по UUID и закрываться, поэтому это не unbounded retention. Однако UUID-keyed registry не означает, что callback не удерживает Player reference.

**Рекомендация:** при следующем изменении callback API рассмотреть передачу UUID в callback и повторное разрешение online Player через Paper adapter непосредственно перед операцией. Не менять поведение и не расширять этот пункт до универсального callback framework без необходимости.

## 2. Проверенные исключения и границы

- RIPS enforcement callback в `RaskolClasses.handleRuntimeIntegrityFailure()` намеренно остаётся plugin-scoped. Duplicate guard, scheduling failure reset и enabled-state guard присутствуют. Не добавлять отмену такого callback при shutdown без отдельного security decision: это может подавить уже принятое enforcement action.
- Foliant storage completion acknowledgement, HP Bar main-thread flush callback и ResourceService completion handoff относятся к A-04 storage completion/drain contract. Они не должны переноситься в этот lifecycle slice.
- Foliant transition callback уже принадлежит отдельному `PlayerCallbackRuntimeOwner`; его UUID re-resolution и quit cancellation не требуют повторной реализации.
- Cooldown notification callbacks освобождают завершённые handles через finally + identity check.
- Installation handlers сохраняют retryable cleanup state; installation sweep останавливается до service shutdown.
- FX-owned charged trails, aura tasks/expiration callbacks и ambient loops принадлежат `FxTaskRegistry`; installation-owned Frost Rune/Pentagram handles остаются в своих handlers.
- Smoke-test на Paper 26.2 был подтверждён пользователем как успешный для shutdown с активными Frost Rune и Pentagram. Это подтверждает проверенный installation/FX path, но не доказывает отказоустойчивость всех независимых shutdown steps.

## 3. Приоритет следующей работы

1. **A-03-003.37 — Root Shutdown Failure Isolation** (P1, рекомендуемый следующий implementation slice).
2. HP respawn callback Player-reference retention (P2, API refinement).
3. TrailListener bounded task ownership (P2, отдельное решение; не блокирует следующий slice).
4. Продолжить repository-wide scan всех scheduled task, callback и listener registration sites.

## 4. Acceptance criteria

- [x] Проверены текущие branch и main refs перед аудитом.
- [x] Проверена root shutdown последовательность на финальном source HEAD.
- [x] Повторно проверены TrailListener task lifetime и self-cancel paths.
- [x] Проверено фактическое поведение UUID-keyed HP callback owner и closures.
- [x] RIPS enforcement callback отделён от обычных cancellable callbacks.
- [x] A-04 storage completion callbacks оставлены в соответствующей области.
- [ ] Полный repository-wide scan всех Java source завершён.
- [ ] Все scheduled task creation sites и listener registrations сопоставлены с owners.
- [ ] Root shutdown failure isolation реализована и покрыта focused tests.

## 5. Ограничения

- Production source и tests не менялись.
- `main` не изменялся.
- `BootstrapCoordinator` не активировался.
- Этот аудит не заменяет новый CI run и не меняет ранее подтверждённый smoke-test результат.


## 6. A-03-003.37 — Root Shutdown Failure Isolation

Реализация добавлена в source после аудита:

- `ShutdownStepRunner` выполняет каждый независимый cleanup step отдельно.
- Каждый `RuntimeException` логируется с диагностическим именем этапа; runner продолжает следующий этап.
- В конце выводится сводка с количеством и именами этапов, завершившихся ошибкой.
- `RaskolClasses.shutdownRuntime()` переведён на runner, включая ранее незащищённые вызовы runtime integrity owner, blueprint unregister, items, passport listener, финальные snapshots, installation cleanup, class provider и active effects.
- Общий `AsyncStorageWriter` по-прежнему закрывается последним. Timeout/неполный drain регистрируется как ошибка отдельного этапа.
- Сохранён dependency order: остановка producers → runtime owners → final snapshots → installation cleanup при доступном FxService → FxService shutdown → оставшиеся runtime cleanup → StorageWriter close.
- Успешные owner close операции по-прежнему очищают соответствующие поля; при исключении ссылка сохраняется, если прежняя реализация также сохраняла её.
- RIPS enforcement callback и A-04 storage completion contract не менялись.

Тесты `ShutdownStepRunnerTest` проверяют, что после одного или нескольких RuntimeException последующие этапы выполняются в исходном порядке, а failure count отражает все неудачные этапы.

### Ограничения реализации

- Изолируются `RuntimeException`, но не JVM-fatal `Error`.
- Runner не выполняет автоматический retry: повторные попытки принадлежат конкретным runtime owners и их retryable cleanup contracts.
- Если один snapshot не удалось поставить в очередь, последующие snapshots и закрытие writer всё равно выполняются best-effort; это не гарантирует сохранность именно неудавшегося снимка.
- Требуется CI на одном финальном HEAD и отдельный Paper 26.2 runtime shutdown smoke test с искусственным/контролируемым отказом одного cleanup step, если его можно безопасно воспроизвести в staging.


### CI checkpoint

- Formatted implementation HEAD: `0d6b97053b30768040e9ea9c3bf92b3f005cd4d4`.
- Build on the pre-format implementation HEAD passed all 680 tests; Analyze correctly flagged only the two Spotless formatting differences.
- Spotless Format #47 completed successfully, applied those formatting-only changes, then ran the full post-format `mvn -Pdev -B verify`.
- Post-format verification on `0d6b9705`: 680 tests, 0 failures/errors/skipped; JaCoCo passed; SpotBugs 0 findings / 0 errors; Spotless 491 Java files clean; `BUILD SUCCESS`.
- The remaining Paper 26.2 runtime smoke test for shutdown failure isolation is not yet performed.


## 7. A-03-003.38 — HP respawn callback UUID re-resolution

### Scope and implementation

- Added a UUID-aware overload to `PlayerCallbackRuntimeOwner.schedule`; the existing `Runnable` contract remains available for current callers.
- `HpAttributeSync` now schedules by UUID and resolves the current online Player when the two-tick callback executes.
- `HpBarService` now schedules by UUID and resolves the current Player from the server before applying max-health, health and hearts state.
- The callbacks no longer capture the Player instance from `PlayerRespawnEvent`.
- Existing timing is preserved: HP Attribute remains delayed by 2 ticks; HP Bar remains next-tick (0 ticks).
- Existing per-player replacement, quit cancellation, stale callback rejection and shutdown ownership semantics are unchanged.

### Verification

- Added a focused owner test proving that the UUID-aware callback receives the tracked player UUID and releases its task handle.
- Full Maven/CI verification is required on the final formatted HEAD.
- Paper 26.2 runtime verification remains required for respawn, quit/reconnect and shutdown behavior.

### Boundaries

- Foliant transition callback continues to use its existing UUID re-resolution path.
- TrailListener tasks remain bounded to 200 ticks and self-cancel; no registry is introduced without evidence that its extra ownership machinery is justified.
- The complete repository-wide scheduler/listener ownership scan remains open.
- A-03-003.37 runtime acceptance remains a separate outstanding gate.
