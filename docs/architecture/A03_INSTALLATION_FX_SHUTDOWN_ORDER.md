# A-03-003.34 — Installation / FX shutdown dependency ordering

## Статус

- Репозиторий: `hayferdahmer/RaskolClasses`
- Ветка: `refactor/a03-foundation-lifecycle`
- Платформа: Paper 26.2 / Java 25
- Изменение: перенос закрытия `FxService` после остановки installation sweep и `InstallationService.shutdown()`.

## Обнаруженная проблема

В `RaskolClasses.shutdownRuntime()` до исправления `FxService.shutdown()` вызывался до закрытия `InstallationRuntimeOwner` и до `InstallationService.shutdown()`.

При этом production cleanup handlers используют FX-сервис:

- `FrostRuneHandler.expire()` вызывает `plugin.getFx().stopAmbient(...)` и `plugin.getFx().impactBurst(...)`.
- `PentagramHandler.expire()` вызывает `plugin.getFx().impactBurst(...)`.

Следовательно, прежний root shutdown order нарушал dependency ordering: installation cleanup мог обратиться к уже остановленному или обнулённому FX-сервису. Ошибка перехватывалась координатором как RuntimeException, поэтому установка сохранялась как cleanup-pending, но штатная попытка cleanup на shutdown не завершалась полностью.

## Исправление

- Удалён ранний вызов `fx.shutdown()` из начала shutdown sequence.
- Сохранён порядок остановки `InstallationRuntimeOwner` перед cleanup установок.
- `InstallationService.shutdown()` теперь выполняется до `FxService.shutdown()`.
- После завершения установки cleanup FX runtime закрывается обычным guarded shutdown-блоком.
- Gameplay scheduling, installation TTL, эффекты и API handlers не менялись.

Новый порядок зависимостей:

1. Закрыть installation sweep producer.
2. Вызвать `InstallationService.shutdown()`, пока FX service ещё доступен.
3. Закрыть `FxService` и принадлежащие ему ambient/FX tasks.

## Границы

- Не добавлялся production debug command или fault-injection switch.
- Не менялась политика retry A-03-003.33.
- Не менялся `main`.
- Live Paper 26.2 runtime smoke test всё ещё требуется; CI не доказывает поведение scheduler на работающем сервере.

## Проверка

- [ ] Full `mvn -Pdev -B verify` на финальном HEAD.
- [ ] Build и Analyze CI на одном финальном HEAD.
- [ ] Spotless Format и повторный verify на том же HEAD.
- [ ] Live Paper 26.2 disable/reload smoke test после получения тестового сервера.
