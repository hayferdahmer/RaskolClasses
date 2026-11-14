# A-03-003.31.1 — AsyncStorageWriter test stability

## Статус

- Репозиторий: hayferdahmer/RaskolClasses
- Ветка: refactor/a03-foundation-lifecycle
- Базовый HEAD: e88a0976a0da70394fd65f03e140db990c9b5db3
- Платформа: Paper 26.2 / Java 25
- Тип работы: test-only stabilization; production-код не менялся.
- Реализация: ee39cd776595404ce5103d32c6c6f2c569d1715c

## Причина нестабильности

Тест `AsyncStorageWriterTest.successfulCompletionRunsOnStorageWorkerThread` использовал два независимых 5-секундных ожидания: основной поток ожидал начала записи, а injected writer одновременно ожидал `releaseWrite` не более пяти секунд.

При задержке CI основной поток мог не успеть зарегистрировать `whenComplete` до истечения второго тайм-аута. Worker завершал future раньше регистрации callback; по контракту `CompletableFuture` callback в таком случае исполняется синхронно на потоке, который регистрирует его (здесь — `main`). Проверка имени потока падала, хотя production writer завершил операцию штатно.

## Исправление

- В тестовом injected writer заменено ограниченное ожидание `releaseWrite.await(5, TimeUnit.SECONDS)` на управляемое `releaseWrite.await()`.
- Тест сначала дожидается старта операции, затем регистрирует completion callback и только после этого разрешает worker завершить запись.
- `finally` всегда вызывает `releaseWrite.countDown()`, поэтому worker освобождается и при assertion failure.
- Остальные тестовые тайм-ауты сохранены как пределы ожидания результата/закрытия.
- Production-код `AsyncStorageWriter`, его executor, очередь, completion semantics и shutdown поведение не менялись.

Это устраняет конкретную гонку в тесте, не маскируя возможный production defect и не меняя контракт callback dispatch.

## Проверки и ограничения

- [x] Повторно сверены refs до изменения: рабочая ветка была на `e88a0976a0da70394fd65f03e140db990c9b5db3`, `main` — на `2e407cbe59276e6af88857bf3a0d93595c55c52c`.
- [x] Изменён только `AsyncStorageWriterTest.java` в production/test code.
- [x] Production source не менялся.
- [x] Полный `mvn -Pdev -B verify` завершился успешно в Build workflow #3337 на итоговом HEAD `724846ed35bdd68d68f43a521cc6619c8bbd292f`.
- [x] Build workflow #3337 SUCCESS на итоговом HEAD.
- [x] Analyze workflow #3337 SUCCESS на итоговом HEAD.
- [x] Spotless и SpotBugs подтверждены успешным Analyze job на итоговом HEAD.
- [ ] Live Paper 26.2 runtime smoke test не выполнялся в рамках этого среза.

`main` не изменялся. `BootstrapCoordinator` не активировался. A-03-003.32 не начинать до зелёных Build и Analyze на одном и том же финальном HEAD.
