# A-03-003.28 — Cooldown notification one-shot task completion ownership audit

## Статус

- Репозиторий: hayferdahmer/RaskolClasses
- Ветка: refactor/a03-foundation-lifecycle
- Проверенный HEAD до аудита: d974d1307d6d4f698a9bcb1f227f6898a7e72ec2
- Платформа: Paper 26.2 / Java 25
- Тип работы: source-backed lifecycle audit; production-код в этом срезе не менялся.
- Область: отложенные уведомления готовности способностей в CooldownManager.

## 1. Подтверждённая реализация

Файл: src/main/java/dev/raskol/classes/ability/CooldownManager.java.

- Поле tasks хранит BukkitTask по составному ключу player UUID + ability ID (строка 53).
- start(...) создаёт delayed notification через Bukkit.getScheduler().runTaskLater(...) (строки 104–128).
- При повторном запуске того же cooldown старый handle удаляется из map и отменяется (строки 104–107).
- При отмене cooldown handle удаляется через cancelTask; при quit менеджер вызывает cancelPlayerTasks (строки 295–316; event handlers находятся выше по файлу).
- CooldownRuntimeOwner.close() вызывает CooldownManager.cancelAllTasks() до снятия listener; ошибки отмены сохраняют возможность retry.
- clear() также вызывает cancelAllTasks().

## 2. Найденный lifecycle gap

В callback, переданном в runTaskLater, нет симметричного удаления завершившегося handle из tasks.

После выполнения callback возможны три обычных исхода:
1. Игрок уже offline — callback возвращается.
2. Cooldown был продлён/заменён — callback возвращается, потому что остаток времени положителен.
3. Уведомление отправлено успешно.

Во всех трёх случаях завершившаяся BukkitTask остаётся в tasks[playerId][abilityId], пока её не заменят, явно не отменят, игрок не выйдет или плагин не будет выключен.

Это не доказывает бесконечный рост без ограничений: map ограничена активными игроками и ID способностей, а повторный cooldown того же ability заменяет старую запись. Но она удерживает устаревшие task handles после естественного завершения и завышает runtime ownership state. Это прямое нарушение ожидаемой симметрии register → complete/release.

Существующий inventory уже отмечал этот риск в разделе 3.4; данный срез подтверждает его по актуальному source и уточняет безопасный способ исправления.

## 3. Требуемое исправление (отдельный implementation slice)

Предлагаемый следующий implementation scope:

- Освобождать завершившийся handle в finally callback независимо от того, был игрок online, cooldown истёк или уведомление отправлено.
- Удалять запись только при совпадении identity: byAbility.remove(abilityId, completedTask). Старый callback не должен удалить новый handle, если тот же cooldown уже был заменён.
- После удаления пустой per-player map удалять через условную операцию tasks.remove(playerId, byAbility).
- Сохранить исходные notification delay, звук, action bar, проверку online и cooldown remaining.
- Сохранить текущую ответственность CooldownRuntimeOwner и retryable shutdown cancellation.
- Добавить focused tests для normal completion, offline early return, extended/replaced cooldown и гонки старого callback с новым handle. Если текущая тестовая инфраструктура не позволяет надёжно проверить Paper scheduler, выделить узкий package-private completion helper/registry и тестировать identity semantics без Bukkit runtime.
- Не переносить persistence или cooldown state из CooldownManager.

## 4. Другие наблюдения, не включённые в этот срез

- FoliantService.executeTransition() планирует next-tick post-transition check через Paper scheduler (проверенный участок около строки 428). Это отдельный короткоживущий callback; его ownership/поведение при player quit следует оценить отдельно, не смешивая с storage completion callbacks A-04.
- RaskolClasses.handleRuntimeIntegrityFailure() планирует one-shot disable callback на Paper main thread. AtomicBoolean не допускает дублирующих постановок, а callback проверяет isEnabled(); явный task handle не хранится. Это security enforcement boundary и отдельный кандидат на узкий аудит.
- FoliantService.saveLocks() completion acknowledgement на main thread относится к storage completion contract и остаётся в границах A-04; повторно не переносится в generic runtime owner.
- TrailListener bounded self-cancelling tasks остаются отдельной ранее зафиксированной границей; в этом срезе не менялись.

## 5. Вывод и границы

- Подтверждён один конкретный lifecycle defect: CooldownManager не освобождает естественно завершившиеся notification handles.
- Не обнаружено оснований объединять cooldown task ownership с общим scheduler registry: существующий CooldownRuntimeOwner уже обеспечивает правильный shutdown boundary.
- Production-код, игровые механики, cadence и persistence не менялись.
- main не менялся; BootstrapCoordinator не активировался.
- Полный repository-wide scan всех 390 Java source files не объявляется завершённым. Данный срез закрывает только конкретную ветку Cooldown notification callbacks и фиксирует следующие независимые кандидаты.

## 6. Acceptance criteria

- [x] Проверен актуальный branch HEAD перед аудитом.
- [x] Прослежено создание, замена, естественное завершение, quit cancellation и shutdown cancellation cooldown notification tasks.
- [x] Подтверждён stale-handle path по source.
- [x] Зафиксировано identity-safe исправление без изменения gameplay semantics.
- [ ] Реализовано освобождение completed handles.
- [ ] Добавлены focused tests на все callback exit paths и replacement race.
- [ ] Полный repository-wide task/registration scan завершён отдельными срезами.
- [ ] CI на финальном implementation HEAD прошёл.


## 7. A-03-003.29 — Реализация освобождения завершённых notification handles

### Изменения

- CooldownManager.start(...) теперь оборачивает тело delayed callback в executeNotificationCallback(...).
- Освобождение выполняется в finally, поэтому оно происходит при offline early return, продлённом cooldown, успешном уведомлении и исключении из callback.
- Callback получает собственный BukkitTask через AtomicReference, заполненный сразу после постановки задачи. Delayed task Paper запускается не раньше следующего scheduler tick; минимальная задержка остаётся 1 tick.
- releaseCompletedTask(...) использует byAbility.remove(abilityId, completedTask): callback старой задачи не удаляет её замену.
- Пустая player map удаляется через taskRegistry.remove(playerId, byAbility), только если registry всё ещё содержит именно эту map.
- Задержка, проверка online, проверка remaining cooldown, звук и ActionBar не менялись.

### Тестирование

Добавлен CooldownManagerNotificationTaskTest:
- освобождение после обычного завершения;
- освобождение после callback без действия (модель offline / продлённого cooldown early return);
- освобождение даже при исключении из callback;
- старый callback не удаляет заменённый handle;
- пустая player map удаляется, а новая map не удаляется устаревшим callback.

Тесты проверяют ownership/release contract без поднятия Paper runtime. Live Paper 26.2 smoke test остаётся отдельной проверкой.

### Границы

- CooldownRuntimeOwner, shutdown retry semantics, cooldown persistence и gameplay timing не менялись.
- main не менялся; BootstrapCoordinator не активировался.
- Полный repository-wide lifecycle scan остаётся открытым.

### Acceptance criteria

- [x] Освобождение выполняется в finally на всех путях callback.
- [x] Identity-safe удаление completed handle.
- [x] Условная очистка пустой player map.
- [x] Focused tests на completion, early return, exception, replacement race и map cleanup.
- [ ] Spotless и полный mvn -Pdev -B verify на финальном implementation HEAD.
- [ ] Live Paper 26.2 runtime smoke test.
