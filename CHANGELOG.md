## [1.14.9] — 2026-10-17

Стабилизационный Sprint 6: закрытие критических находок статического аудита 1.14.8.

### Added
- **Spec2Tree.isReachable** (6-01): проверка достижимости узла при текущем стоп-листе.
- **Spec2Service.disabledSet** (6-01): единая точка чтения стоп-листа для сервиса, GUI, selftest.
- **Spec2Tree.prereqsMet/canBuy со стоп-листом** (6-01): отключённый пререквизит считается выполненным.
- Selftest: чек 122 — достижимость всех включённых узлов при текущем стоп-листе; всего 122 чека.
- **ConfigMigrator.migrate148** (8-01): миграция `avoidance`/`hp-display` из `attributes.*` в корень при старте на серверах, обновляющихся с 1.14.6.
- **purgeStale** для 4 китов (6-02): `HunterAbilities`/`RogueAbilities`/`MageAbilities`/`PriestAbilities` — чистка протухших бафф-меток общей purge-задачей.
- Подключение `ProcService.purgeStale()` и `CombatService.purgeBurstLog()` в общую purge-задачу (6-02): методы существовали, но не вызывались.

### Changed
- **Стоп-лист больше не блокирует включённые узлы** (6-01): 25 узлов, включая 6 ультов (`bm_bestial_wrath`, `su_shadow_blades`, `sv_serpent_sting`, `as_deathmark`, `fury_bladestorm`, `guard_last_stand`), снова достижимы при отключённых пререквизитах.
- **Синхронный IO на quit/клик переведён на in-memory + периодический persist** (5-01):
  - `CooldownManager.onQuit`: убран `saveAtomic`, секция фиксируется в in-memory store, persist в autosave/onDisable.
  - `Spec2Service.purchase/chooseMain/resetTree/resetNode`: убраны 4 синхронных `storage.save()`, persist в autosave-задаче.
  - `ResourceService.onQuit`: убран `saveAtomic`, секция фиксируется в in-memory store, persist в autosave/onDisable.
- **Подсказка цены респека** (6-03): `TalentsTab.nodeItem` передаёт UUID просматривающего игрока, а не первого онлайн — цена в lore соответствует рангам игрока, а не случайного.
- **Selftest из консоли** (7-03): `probe = sender instanceof Player sp ? sp : null` — запуск не от игрока больше не мутирует данные первого онлайн-игрока; probe-чеки уходят в skip с предупреждением.
- **Логирование исключений в кастерах** (6-04): `AbilityRegistry.castOn` логирует полный стектрейс (`Logger.log(Level.WARNING, msg, ex)`), а не только `getMessage()` (который у NPE равен null).
- **TalentsTab.nodeItem**: пререквизит из стоп-листа подсвечен как выполненный («✔ … (в разработке — учтён)»), чтобы игрок видел причину доступности узла.

### Fixed
- **Регрессия 1.14.8 (6-01, CRITICAL)**: 25 включённых узлов (включая 6 ультов) стали недостижимы из-за disabled-пререквизитов — `prereqsMet` требовал ранг отключённого узла. Фикс: отключённый пререквизит считается выполненным (вырезан из дерева).
- **Утечки памяти (6-02, HIGH)**: статические карты китов (`TRUE_SHOT_UNTIL`, `COLD_BLOOD_UNTIL`, `SHADOW_BLADES_UNTIL`, `VENDETTA_EXPIRY`, `COMBUSTION_UNTIL`, `LAST_HEAL`) росли до перезапуска; `ProcService.purgeStale()` и `DamageCaps.purge()` существовали, но не вызывались.
- **Синхронный IO на главном потоке (5-01, HIGH)**: `CooldownManager`/`Spec2Service`/`ResourceService` писали файлы синхронно на quit/клик — при массовом выходе блокировали тик.
- **Миграция конфига (8-01, HIGH)**: на серверах, обновляющихся с 1.14.6, баланс уклонения молча менялся (parry-k 300→150) — код читал корень (где ключей нет) и брал дефолты.
- **Подсказка цены респека (6-03, MEDIUM)**: показывала цену по рангам другого игрока (первого онлайн), а не просматривающего.
- **Selftest из консоли (7-03, MEDIUM)**: менял данные первого онлайн-игрока (ранги, спеку, HP).
- **Потеря стектрейса (6-04, MEDIUM)**: `getMessage()` у NPE равен null, ошибку в кастере нельзя было локализовать.

### Migration
- **Серверы с 1.14.6**: при первом старте 1.14.9 в логе появятся warning'и `Миграция 1.14.8: attributes.avoidance.* → avoidance.*` и `attributes.hp-display.* → hp-display.*`; `config.yml` обновится автоматически. Рекомендуется удалить старые секции вручную после подтверждения миграции.
- **Окно потери при краше**: для кулдаунов/очков путей/ресурсов ≤ `storage.autosave-minutes` (дефолт 5 минут) — приемлемо для короткоживущих данных (та же философия, что P1-7 у HpBarService).

### Testing
- Selftest: 122 чека, все PASS.
- Чек 122: достижимость всех включённых узлов при текущем стоп-листе.
