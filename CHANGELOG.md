<p align="center">
  <img src="docs/assets/headers/changelog.svg" alt="CHANGELOG — Летопись версий" width="100%"/>
</p>

# Changelog

Все заметные изменения проекта документируются в этом файле.
Формат основан на [Keep a Changelog](https://keepachangelog.com/),
семантическое версионирование по [SemVer](https://semver.org/).

---

## [Unreleased]

### Architecture Refactoring — A-03

- Добавлены базовые контракты жизненного цикла модулей: состояния, контекст, интерфейс PluginModule и проверка допустимых переходов.
- Добавлена независимая от Bukkit область владения задачами с безопасной отменой, агрегированием ошибок и повторной попыткой для задач, отмену которых не удалось подтвердить.
- Добавлен типизированный ServiceRegistry с фазой регистрации и явным seal перед разрешением зависимостей; обязательные и необязательные сервисы имеют разные контракты.
- Добавлен детерминированный ModuleDependencyResolver с проверкой повторяющихся ID, отсутствующих зависимостей, самозависимостей и циклов.
- Добавлен BootstrapCoordinator: фазовый запуск модулей, проверка графа до побочных эффектов, откат при ошибке и обратный порядок остановки/закрытия.
- ServiceRegistry получил явное закрытие: после shutdown доступ к сервисам запрещён, ссылки на зарегистрированные экземпляры освобождаются.
- Добавлены unit-тесты для lifecycle, управления задачами, реестра сервисов и графа модулей. Интеграция Foundation с существующими сервисами будет выполняться отдельными проверяемыми этапами.

## [1.15.8] — «Подписанный рубеж» · 2026-11-13

### RIPS: официальная подпись артефакта и защита целостности

**Официальный артефакт:**
- Добавлен packager официальной сборки: в JAR включаются build identity, канонический integrity manifest, detached Ed25519 signature и публичный ключ.
- Подпись создаётся из защищённого GitHub Environment Secret; приватный ключ не включается в JAR, репозиторий или аргументы командной строки.
- До подписания проверяются пути ZIP-записей, нормализация имён, дубли и коллизии; после упаковки повторно проверяются архив и точное содержимое подписанных записей.
- Релизный workflow формирует OFFICIAL JAR при запуске по тегу `vX.Y.Z`. Отдельный ручной workflow позволяет проверить подпись и получить временный артефакт без создания тега и публикации GitHub Release.

**Проверки и тесты:**
- Добавлен интеграционный тест runtime-подмены: изменение защищённого файла в изолированном тестовом JAR должно обнаруживаться монитором и передаваться в enforcement policy.
- Добавлен RIPS Security Auditor v1: read-only статический аудит исходников и запуск профильных Java-тестов. Аудитор — дополнительная проверка, а не полноценный vulnerability scanner и не доказательство абсолютной безопасности.
- Уточнена проверка формата подписи и публичного ключа, а также соответствие правил валидации ZIP-архива между packager и runtime verifier.

**Сборка и качество:**
- Версия Maven-проекта установлена в стабильное значение `1.15.8` без суффикса `-SNAPSHOT`.
- Нормализовано форматирование Spotless, добавлена конфигурация JaCoCo и централизованы версии Maven-плагинов.
- Целевая сборочная конфигурация 1.15.8 переведена на JDK 25, Java release 25, Paper API `26.2.build.135-stable` и `api-version: '26.2'`.

**Фактическая проверка релизного кандидата (2026-11-13):**
- GitHub Actions для проверенного commit: Build, Analyze и signing-verification — успешно; Release job в ручном verification run пропущен ожидаемо, поскольку публикация запускается по тегу.
- Тесты: **453 прогона, 0 ошибок, 0 падений, 0 пропусков**.
- Signing-verification подтвердил политику подписи и официальный OFFICIAL-артефакт; SHA-256 JAR: `579c53a3c1af314b42bcd2612787c837a82bab5ee5a024536598f0194755c`.
- На реальном сервере Paper 26.2 (сборка `26.2-112-c9e894d`) + Java 25 стартовая RIPS-проверка завершилась `VERIFIED`, `checkedFiles=408`.
- Runtime Integrity Monitor через 30 минут перешёл `NOT_STARTED -> RUNNING -> VERIFIED`.
- Плагин завершил включение; конфигурация валидна, загружены 270 определений предметов и 36 шаблонов специализаций, подключены RaskolCore, PlayerPoints API и PlaceholderAPI.

**Известные ограничения, не блокирующие запуск:**
- Восемь деревьев специализаций имеют effective capacity ниже `tree-capacity-min=50`; минимальное значение у `beastmaster` — 28. Требуется отдельная ревизия структуры/ёмкости талантов.
- Интеграция RaskolEnchant обнаруживается, но загрузка чертежей и регистрация рецептов пока не реализованы.
- В 1.15.8 выбор специализации не запрещает полностью прокачивать другие ветки. Пересмотр правил специализации и ограничений талантов запланирован на 1.16.0; в рамках стабилизации 1.15.8 это поведение не менялось.

## [1.15.7] — дата выпуска не подтверждена в истории репозитория

### Завершение аудиторских волн и стабилизация боевых подсистем

- Боевые проки и способности Hunter направлены через общий `CombatService.dealDamage`, чтобы проходить единый боевой конвейер.
- Исправлена регистрация обработчика веерных стрел Hunter; добавлена регистрация AvoidanceService как listener.
- При отключении плагина PetService вызывает `despawnAll()`, чтобы боевые петы не оставались в мире после выгрузки.
- Продолжена стабилизация spec2, хранилища, FX и конкурентных участков; обновлены CI-проверки и лицензионные заголовки до RASKOL Proprietary License v2.0.

**Примечание по истории:** дата 1.15.7 не восстанавливается из текущего CHANGELOG; она намеренно не подменена предположительной датой.

## [1.15.6] — 2026-11-06
### Волны аудита 3–6: боевой конвейер, spec2-контракты, конкурентность, хранилище, FX
Релиз, закрывающий **35 находок** статического аудита по подсистемам боя, spec2,
хранилища, FX, атрибутов и selftest. Selftest вырос с 150 до 158 чеков, включая
8 боевых и архитектурных canary (путь A мёртв → красный сразу).

**Волна 3 — боевой конвейер (аудит 2.6-30/31/32/34, 2.6-97):**
- `DotService.computeTicks` — чистая функция (принимает `List<DotInstance>`, возвращает `List<DotInstance>`); hot-путь тика тестируется изолированно.
- `CombatService.dealDamage` — 5-параметрическая сигнатура с `fromProc`: health-guard, canHit, школы, капы и suppress-флаги применяются единообразно.
- `VanillaDamageListener` — школа урона читается из `CombatService.currentCastSchool()` (ThreadLocal), а не hardcoded `ARCANE`.
- `HudService.renderDots` — mutable `ArrayList(snapshot).sort()` перед упорядочиванием; `List.copyOf`-immutable больше не падает `UnsupportedOperationException`.

**Волна 4 — spec2-контракты и путь A (аудит 2.6-1/40/89/90/13/105):**
- **Путь A восстановлен (2.6-1 CRITICAL):** `VanillaDamageListener` регистрируется явно в `onEnable`. С 1.14.10 слушатель создавался в конструкторе `CombatService`, но `registerEvents` для него не вызывался — резисты, капы, уклонение, WeaponMastery на авто-атаках и профили RaskolMobs были мертвы.
- **burst-undo контракт (2.6-40):** `DamageCaps.undoLastBurst(Player, double)` + `@EventHandler(priority=MONITOR)` на `onDamageMonitor` — burst-лог откатывается при отменённом ударе.
- **per-tree row-gates (2.6-89):** `spec2.row-gates-by-tree.beastmaster = [0,5,10,15,20,27]` в `config.yml` открывает ульт `bm_bestial_wrath` с 27 очков.
- **Платёжный контракт (2.6-90):** `EconomyHook.withdraw` возвращает `boolean`; `Spec2Service.refundCoins` возвращает монеты через Vault-reflection при PRUNED-rollback.

**Волна 5 — конкурентность, хранилище, FX (аудит 2.6-4/86/96/102/103):**
- **AttributeModifiers sync (2.6-86):** `addTimedModifier`/`addPermanentModifier` держат `synchronized(list)` на время `removeIf + add`.
- **Spec2Storage purge (2.6-96):** `purgeForeignTrees` в `reconcile` удаляет ранги деревьев чужих классов после смены LP-группы.
- **FxService per-player proc (2.6-102):** ключ `procVisualCd` = `uuid + ":" + procId`.
- **FxService aura CAS (2.6-103):** `startAura` сравнивает `current == scheduledTask` по identity — новая аура не снимается отложенной задачей старой.
- **BLOOD_FURY через dealDamage (2.6-4):** возвратный урон идёт через `combat.dealDamage(..., fromProc=true)`; ThreadLocal-гард исключает взаимную рекурсию.

**Волна 6 — selftest-гигиена (аудит 2.6-108, 2.12-1):**
- CC-чеки 82–84/90 skip'ятся, если у probe УЖЕ активен контроль.
- Kill-switch `selftest.allow-live-probe` в `config.yml` (default `true`).
- CHANGELOG backfill: восстановлены `1.15.1`, `1.14.12`, `1.14.13`, `1.14.1`–`1.14.3`, `1.14.5`.

**Документация:**
- README: убран ASCII-схема корон и раздел «Как устроено оформление»; выровнены отступы и ширины таблиц и эмблем; счётчик selftest и регресс-матрица приведены к `SelftestRunner` (167 чеков).

**Файлы:** `attribute/AttributeModifiers.java`, `fx/FxService.java`, `effect/ActiveEffectManager.java`, `combat/VanillaDamageListener.java`, `combat/DamageCaps.java`, `combat/CombatService.java`, `spec/storage/Spec2Storage.java`, `spec/service/Spec2Service.java`, `selftest/SelftestRunner.java` (158 чеков), `config.yml` (блоки `spec2.row-gates-by-tree`, `selftest.*`).

---

## [1.15.5] — 2026-10-26
### UI/UX: hover-превью, браузер с фильтрами, расширенный stats
**Новое:**
- Hover-превью предметов в чате через `ItemHover` (`showText` с displayName+lore — `showItem` не рендерит кастомное lore, решение зафиксировано в javadoc).
- `ItemBrowserMenu`: фильтры класс/редкость кнопками-циклами (слоты 46/47), поиск аргументом команды `/rc item browser <запрос>` (без chat-capture), pure-метод `matches()` для headless-чека.
- `/rc item stats` расширен: построчный вывод экипировки с hover по каждому слоту, причина каждого unmet-требования (класс/спек/уровень), прогресс к следующему порогу каждого надетого сета.
- `/rc item sets [ник]` (новая): активные пороги + потенциал (след. порог / «комплект полный»); считает только eligible-предметы (консистентно с reconcile).
- Сообщения salvage/craft несут hover-превью предмета (превью снимается клоном стака ДО потребления).
- Selftest-чеки 138–139: hover-превью + pure-фильтр браузера (9 сценариев). Итого **139 чеков**.

**Изменено:**
- `messages.item.*` +1 ключ: `sets-none` (итого 6).
- `ItemSub`: добавлена подкоманда `sets`; `stats` расширен; `browser` принимает поисковый запрос аргументами.
- `/rc item debug` удалён — ссылался на несуществующий `ItemPdc.uidOwner` (compile-фикс волны B).

**Файлы:** `ItemHover.java` (новый), `ItemBrowserMenu.java` (расширение), `ItemSub.java` (расширение), `SalvageService.java`/`CraftingService.java` (hover в сообщениях), `SelftestRunner.java` (139 чеков).

---

## [1.15.4] — 2026-10-26
### Крафт и фрагменты
**Новое:**
- Фрагменты — 13 видов расходной валюты крафта (6 осколков weapon/armor × тир, 6 эссенций классов, 1 реликвия). PDC-формат `frag_v/frag_id` изолирован от `item_v`.
- Разбор (`/rc item salvage`): предмет в руке → фрагменты. Pure-математика: `itemLevel/20` осколков + классовая эссенция (RARE=1, EPIC+=2) + реликвия для LEGENDARY/MYTHIC. Дневной лимит (`item.salvage.daily-limit=20`) — анти-ферма.
- Крафт (`/rc item craft [id]`): фрагменты + монеты → предмет. `crafting.yml` извлекается из jar при первом старте. Компенсация монет при ошибке создания через reflection-доступ к Vault Economy (pom.xml без compile-зависимости).
- GUI крафта (пагинация 45 иконок, live-счётчики фрагментов в lore, авто-обновление).
- Selftest 135–137: реестр фрагментов (13), pure-математика `SalvageService.fragmentsFor`, загрузка рецептов `crafting.yml`.

**Изменено:**
- `ItemSystem`: конструктор создаёт `FragmentRegistry`/`SalvageService`/`CraftingService`/`CraftingGui`; `CraftingGui` регистрируется как Listener; `crafting.yml` перечитывается вместе с `items.yml` по `/rc item reload`.
- `messages.item.*`: +12 ключей (salvage-*/craft-*).

**Файлы:** `Fragment.java`, `FragmentRegistry.java`, `SalvageService.java`, `CraftingService.java`, `CraftingGui.java`, `crafting.yml` (все новые).

---

## [1.15.3] — 2026-10-26
### Локализация предметного слоя
**Новое:**
- Блок `messages.item.*` в `config.yml` — локализация сообщений предметного слоя (EquipGuard: запреты экипировки; ItemFactory: lore-маркер; DebugSub: владение).
- Selftest-чек 134: canary «живой `config.yml` получил блок 1.15.3» — 5 ключей `messages.item.*` присутствуют и непустые.

**Файлы:** `config.yml` (блок `messages.item.*`), `SelftestRunner.java` (чек 134).

---

## [1.15.2] — 2026-10-25
### Реализм урона оружия (WeaponMastery)
**Новое:**
- Матрица владения оружием 8 типов × 6 классов: `×1.00` (native), `×0.75` (familiar), `×0.50` (foreign).
- Множитель применяется к PHYSICAL-урону в `applyOutgoingOffense` (после base+add, до stealth/amplifier/crit).
- Строка разбора в `/rc debug <ник>`: «Владение оружием: SWORD → NATIVE (×100.0% урона)».
- Selftest-чеки 131–133: матрица 48 ячеек, конкретные пары WARRIOR/HUNTER, null-защита.

**Матрица:**
| Класс    | Native           | Familiar        | Foreign                    |
|----------|------------------|-----------------|----------------------------|
| WARRIOR  | SWORD/AXE/MACE   | DAGGER          | BOW/CROSSBOW/STAFF/WAND    |
| HUNTER   | BOW/CROSSBOW     | DAGGER/AXE      | MACE/SWORD/STAFF/WAND      |
| ROGUE    | DAGGER           | SWORD           | все остальные              |
| MAGE     | STAFF/WAND       | —               | все остальные              |
| PRIEST   | MACE             | STAFF/WAND      | все остальные              |
| WARLOCK  | WAND             | STAFF           | все остальные              |

**Файлы:** `WeaponMastery.java` (новый), `VanillaDamageListener.java`, `DebugSub.java`, `SelftestRunner.java`.

---

## [1.15.1] — 2026-10-23
### Предметный слой: декомпозиция и запреты (итерации A–D)
**Новое:**
- A: `ItemPdc`/`ItemFactory`/`AffixRoller` вынесены в `item.factory`; PDC-ключи v1 зафиксированы.
- B: `ItemConfigLoader` декомпозирован (ItemValidator), `items.yml` schema v1 + `settings.budget`.
- C: `UnbreakableGuard` — страховка прочности legacy-предметов 1.15.0.
- D: `EquipGuard` — классовые/спек/уровневые запреты экипировки (клик/shift/drag/F-swap);
  `UidRegistry` instance-класс; `SetBonusService.apply()` — pure (возвращает новую map);
  `ItemHook.reconcile` — O(n) кэш экипировки.
- Сообщения запретов вынесены в `messages.item.*` (1.15.3).

**Файлы:** `item/factory/ItemPdc.java`, `item/factory/ItemFactory.java`, `item/factory/AffixRoller.java`, `item/guard/EquipGuard.java`, `item/guard/UnbreakableGuard.java`, `item/set/SetBonusService.java`, `item/registry/UidRegistry.java`.

---

## [1.15.0] — 2026-10-22 (ядро оружия и брони)

### Добавлено

- Каталог из 342 именованных предметов: девять семейств оружия и четыре части брони для шести классов, уровни 20/40/60, редкости COMMON/RARE/EPIC.
- Шесть тематических пятичастных сетов с накопительными бонусами за 2/4/5 частей и названиями, привязанными к миру «РАСКОЛ | ДВЕ КОРОНЫ».
- Ресурс-пак Minecraft Java 1.21.4 с 25 авторскими иконками оружия и брони, публикуется рядом с JAR; шаблоны задают `custom-model-data`.
- Шаблонный бюджет статов, детерминированный ролл взвешенных аффиксов и пороги сетов 2–6.
- Версионированный PDC `raskolclasses` с UID экземпляра, seed, проверкой определения и зажимом значений аффиксов.
- Экипировочные снапшоты, обновление при событиях инвентаря/слотов и периодическая сверка; для стрел сохраняется оружие на момент выстрела.
- Команды `/rc item` и `/rc set`, браузер предметов, вкладка «Снаряжение», публичные Items API и CombatProfile API для интеграций.
- Опциональная выдача стартового комплекта COMMON ур. 1 при назначении класса (`item.starter-kit.enabled`, выключено по умолчанию); `/rc debug profile` для проверки боевых профилей мобов.
- Selftest 128–130 проверяет реестр 270+ определений, базовые инварианты предметов и отсутствие зависимости от RaskolGear.

### Изменено

- Статы предметов подключены к атрибутам, HP, урону, силе, критам, пробитию, резистам, ресурсам, КД, скорости, контролю и отражению.
- `items.yml` загружается с проверкой структуры; reload сохраняет прежний реестр при структурной ошибке.
- Имена вариантов показывают русскую редкость и уровень; несколько типов оружия одного класса получают уникальные ID.
- Покупка/продление raskolspec+ доступны через `/rc plus buy [периоды]` и кнопку каталога; списание компенсируется при ошибке записи срока.
- Доступ raskolspec+ повторно проверяется в момент применения; `plus.enabled: false` действительно открывает шаблоны в публичном тестовом режиме.

### Удалено и несовместимость

- Полностью удалены RaskolGear-хук, старые set-бонусы, команда `/rc gear` (сохранён алиас диагностики `/rc gear`) и старая вкладка GearTab.
- Старые предметы RaskolGear не распознаются и не переносятся. Перед запуском 1.15.0 удалите RaskolGear с сервера; старые предметы при необходимости перевыдайте.

### Ограничения

- Для уклонения, парирования, блока и lifesteal предметных статов в 1.15.0 нет.
- Стартовые наборы генерируются встроенными шаблонами; сервер может заменить их секциями `templates` в `items.yml`.
- Стартовый комплект при выборе класса отключён по умолчанию.

---

## [1.14.16] — 2026-10-22 (raskolspec+: доступ и применение шаблонов)

### Исправлено

- Применение кураторского билда перепроверяет право доступа непосредственно перед
  изменением дерева: истёкшая или отозванная подписка не проходит через ранее открытый
  экран подтверждения.
- `plus.enabled: false` теперь действительно открывает шаблоны в публичном тестовом
  режиме, как обещает комментарий конфигурации; статус подписки при этом по-прежнему
  показывает фактическое состояние подписки.
- Добавлена команда `/rc plus buy <периоды>` для самостоятельной покупки/продления за
  PlayerPoints. Сумма списывается только при доступном кошельке, предел берётся из
  `plus.max-gift-periods`, а при ошибке записи подписки плагин пытается вернуть списание.
- Для покупки и подарка ошибка атомарного сохранения срока не подтверждает операцию;
  плагин пытается компенсировать списание.
- Админские команды grant/revoke теперь сообщают об ошибке записи вместо ложного
  подтверждения успешной выдачи или отзыва.
- Подарки и админские status/grant/revoke работают с известными оффлайн-игроками без
  блокирующего сетевого запроса за профилем.
- Администраторы с `raskolclasses.admin.plus` могут просматривать и применять шаблоны
  через GUI; административный путь применения остаётся бесплатным.
- README обновлён: удалено устаревшее предупреждение о preview-применении, указана
  актуальная версия артефакта.

### Совместимость

- Миграций нет. Все прежние `plus-subscription.yml` сохраняются.
- Paper 1.21.4, Java 21.

---

## [1.14.15] — 2026-10-22 (raskolspec+: подписка на курируемые билды — инфраструктура и GUI-оболочка)

Первая волна монетизируемого слоя «идеальных билдов»: 36 курируемых
шаблонов (18 спек × PvP/PvE), подписка за донат-валюту «Империал» (PlayerPoints),
  подарки и GUI-каталог с тизер-моделью.

### Новое: слой raskolspec+ (`spec/build/*`, `gui/PlusGui`, `command/sub/PlusSub`)

- **`builds.yml`** — ресурс jar, извлекается в `plugins/RaskolClasses/builds.yml` при
  первом старте; правится админом без пересборки; `/rc reload` перечитывает.
  36 шаблонов: имя, tagline, штамп `updated`, ранги узлов.
- **`BuildTemplates`** — загрузчик + валидатор: существование спеки/узлов, ранг ≤ maxRank,
  prereq-замкнутость, сумма очков ≤ `spec2.max-points`; узел из `spec2.disabled-nodes`
  → шаблон `stale` («⚠ обновляется после баланс-патча», не применим). Ошибочный шаблон
  отключается точечно с warning, остальные работают.
- **`ImperialWallet`** — абстракция кошелька: `PlayerPointsWallet` (reflection над
  PlayerPoints API, без compile-зависимости) и `NullWallet` (фолбэк, если плагина нет —
  подписка не продаётся, чтение/гранты работают).
- **`PlusStorage`** — `plus-subscription.yml` (expiry + gift-note), write-through через
  `SafeStorage.saveAtomic`.
- **`PlusSubscription`** — grant (продление **суммирует** дни, купленное не сгорает),
  revoke, gift (атомарно: take у дарителя → grant получателю; не списалось → не подарили),
  оффлайн-нота подарка на вход, LP-синхронизация `raskolclasses.plus.active` через
  `settemp` (для DeluxeMenus view_requirement).
- **`PlusGui`** — два экрана (CATALOG/CONFIRM) на holder-паттерне `PlusHolder`
  (как `FoliantHolder`): статус-слот, карточки PvP/PvE текущей спеки, confirm с двойным
  кликом (взвод 30 с). Тизер-модель Dota Plus: без подписки видны имя/tagline/счётчики
  узлов и «🔒 требуется raskolspec+», клик шлёт upsell с ценой и путём покупки.
- **`/rc plus`** — роутер: `[status [ник]] · gift <ник> <периоды> · grant <ник> <n> ·
  revoke <ник>`; gift с rate-limit `plus.cooldown-gift-ms` и лимитом 1–12 периодов;
  права `raskolclasses.plus.use` / `raskolclasses.admin.plus` (plugin.yml).
- **Плейсхолдер** `%raskolclasses_plus%` — дни до истечения или `expired` (TTL-кэш 1 с).
- **`RaskolConfig`** — дефолты `plus.*` (enabled, price-per-period=100, period-days=30,
  max-gift-periods=12, cooldown-gift-ms=5000) + `messages.plus.*`; новые хелперы
  `getIntOr/getLongOr/getBoolOr`.
- **Selftest 123–125**: валидность 36 шаблонов; математика grant/remainingDays/revoke;
  stale-маркировка. Итого **125 чеков**.
- Для внешней витрины доступна админская команда `/rc plus grant`; отдельный конфиг
  DeluxeMenus в этот исходный архив не включён.

### Preview-ограничение (ВАЖНО для монетизации)

- Применение шаблона в confirm-экране сейчас возвращает OK-сообщение, но **не изменяет
  билд**: реальная логика (resetTree + setRanks + reconcile + экономика респека дерева)
  поставляется волной **15C в релизе 1.14.16**.
- Операционная рекомендация: **не деплоить `raskolspec.yml` в DeluxeMenus и не анонсировать
  подписку игрокам до выхода 1.14.16**. Инфраструктура (гранты, подарки, плейсхолдер, GUI)
  полностью рабочая и тестируемая уже сейчас.

### Сопутствующие фиксы волн 15A/15B (compile-стабилизация)

- `PlusGui`: удалён фантомный импорт `BookSession`; маршрутизация кликов переведена с
  парсинга `title()` (examinableName() возвращает тип Adventure-компонента, а не текст)
  на `InventoryHolder` с enum-экраном.
- `SelftestRunner`: 12 вызовов `resetAllDR(...)` → каноническое `resetAllDr(...)`.
- `BuildTemplates`: `Spec2Tree.nodeById(...)` → `find(...)`; в тесте over-budget сумма
  исправлена на фактические 47 очков.
- `RaskolClasses`: `plus.period-days` читается через `getConfig()` (внутри JavaPlugin-наследника
  нет поля `plugin`); `PlusGui` создаётся и регистрируется до `registerCommand()`.
- `PlusSub`: ключи сообщений без префикса `messages.` (RaskolConfig.message добавляет его сам).

### Миграции и совместимость

- Миграций конфигов/данных/мира нет. `builds.yml` и `plus-subscription.yml` создаются сами.
- Breaking changes: нет. Paper 1.21.4, Java 21.

---

## [1.14.14] — 2026-10-21 (SpotBugs-гигиена: закрытие 12 findings + регресс-фикс 13C)

Гигиенический патч, закрывающий все 12 findings отчёта `spotbugsXml.xml` от
прогона 1.14.13 (9 кодом + 3 обоснованными exclude). Побочно закрыта
регрессия 13C: рассинхрон карт состояния `WarlockAbilities` между тремя
местами создания экземпляра (AbilityRegistry, TreeAbilities, RaskolClasses).

### Закрытые findings (12/12)

#### Исправлены кодом (9)

| # | Pattern | Класс | Правка |
|---|---|---|---|
| 1 | `ST_WRITE_TO_STATIC_FROM_INSTANCE_METHOD` | `WarlockAbilities` | Конструктор приватизирован; создание через `WarlockAbilities.install(plugin)` — статическую фабрику с idempotency-guard |
| 2 | `ST_WRITE_TO_STATIC` (регрессия 13C) | `AbilityRegistry`, `TreeAbilities`, `RaskolClasses` | Все 3 точки создания переведены на `install()` → **один** экземпляр синглтона; карты `sealExpiry/sealAmp/antihealExpiry/channelTasks` снова общие для китовых кастеров, древесных кастеров, слушателя `onPlayerQuit` и статик-facade |
| 3 | `BC_VACUOUS_INSTANCEOF` | `DotService.onProjectileHit` | Убран vacuous `instanceof Projectile` (`event.getEntity()` всегда возвращает `Projectile`); заменено на прямое присваивание + null-guard |
| 4 | `BC_VACUOUS_INSTANCEOF` | `VanillaDamageListener.onDamage` | `attacker instanceof Player` заменён на `attacker != null` (переменная уже типизирована `Player`) |
| 5 | `FE_FLOATING_POINT_EQUALITY` | `VanillaDamageListener.applyOutgoingOffense` | `total != base` → `Math.abs(total - base) > 1e-9` (epsilon-сравнение в боевой логике офенса) |
| 6 | `URF_UNREAD_FIELD` | `LuckPermsBackend.config` | Удалено мёртвое поле и сеттер `setConfig()`; висячий вызов убран из `ClassProvider` |
| 7 | `HE_INHERITS_EQUALS_USE_HASHCODE` | `FlavorPlaceholder` | **Откат override** — `equals()` объявлен `final` в апстриме PlaceholderAPI 2.11.6, переопределение невозможно (compile error). Закрыт точечным exclude |
| 8 | `HE_INHERITS_EQUALS_USE_HASHCODE` | `RaskolPlaceholder` | То же самое — см. выше |
| 9 | `FE_FLOATING_POINT_EQUALITY`/`FL_FLOATS_AS_LOOP_COUNTERS` | `BalanceSimulator`, `SelftestRunner` | Test-only классы — закрыты exclude с обоснованием «осознанные равенства формульных значений» |

#### Обоснованные exclude (3 правила в `spotbugs-exclude.xml`)

| Правило | Scope | Обоснование |
|---|---|---|
| `FE_FLOATING_POINT_EQUALITY` + `FL_FLOATS_AS_LOOP_COUNTERS` | `BalanceSimulator`, `SelftestRunner` | Test-only симуляторы TTK; равенства в формулах осознанные, epsilon не требуется |
| `URF_UNREAD_FIELD` | `PetService$PetState.dmgMult/speedMult` | Зарезервированы для будущего pet-damage слоя 1.15.0 — исключены до появления читателей |
| `HE_INHERITS_EQUALS_USE_HASHCODE` | `*Placeholder` (2 класса) | Апстрим PlaceholderAPI 2.11.6: `equals()` final в `PlaceholderExpansion`, переопределение невозможно; реестр расширений PAPI ведётся по строковому `identifier`, объект-ключом не используется — реального риска нет |

### Побочное закрытие регрессии 13C

В волне 13C `WarlockAbilities` была переведена со static-mutable state на instance-поля
с singleton-фасадом, но конструктор оставался публичным. В `onEnable` экземпляры
создавались трижды:

1. `new AbilityRegistry(this)` → `new WarlockAbilities(plugin)` внутри (кастеры слотов 1–5) → `instance = №1`
2. `TreeAbilities.registerCasters(...)` → `new WarlockAbilities(plugin)` (кастеры слотов 6+) → `instance = №2`
3. `this.warlockAbilities = new WarlockAbilities(this)` (слушатель `onPlayerQuit`) → `instance = №3`

**Симптомы регрессии** (молчаливо с 13C):
- `ruin_seal` писал seal-метки в `sealExpiry` №1/№2, а `CombatService` читал пустую карту №3 → амплификация ×1.26 не работала
- `soul_rift` складывал канал-задачи в `channelTasks` №1/№2, а `onPlayerQuit` чистил пустую карту №3 → задачи не отменялись на выход игрока
- `addAntiheal` писал в №1/№2, а `HpBarService.heal` проверял №3 → анти-хил не блокировал лечение

**Фикс:** конструктор приватизирован, создание только через `WarlockAbilities.install(plugin)` с idempotency-guard → **один** экземпляр на всё. Selftest не ловил эту регрессию (чек 37 проверял пустоту реестров, а не сквозную проводку) — добавлен ручной регресс-тест в чеклист приёмки.

### Изменения файлов

**Правки кода:**
- `ability/WarlockAbilities.java` — конструктор `private`, добавлена `install(plugin)`
- `ability/AbilityRegistry.java` — `WarlockAbilities.install(plugin)` вместо `new`
- `ability/TreeAbilities.java` — `WarlockAbilities.install(plugin)` вместо `new`
- `combat/VanillaDamageListener.java` — epsilon-сравнение в `applyOutgoingOffense`, `attacker != null` в `onDamage`
- `combat/dot/DotService.java` — упрощён `onProjectileHit`
- `classsystem/LuckPermsBackend.java` — удалены поле `config` и `setConfig()`
- `classsystem/ClassProvider.java` — удалён вызов `backend.setConfig(config)`
- `hook/FlavorPlaceholder.java` — откат overrides equals/hashCode (невозможны из-за final в апстриме)
- `hook/RaskolPlaceholder.java` — откат overrides equals/hashCode
- `RaskolClasses.java` — `WarlockAbilities.install(this)` вместо `new`

**Инфраструктура:**
- `spotbugs-exclude.xml` — добавлены 3 обоснованных правила (test-only, пет-бэклог, PAPI-ограничение)
- `pom.xml` — версия 1.14.12 → **1.14.14**
- `plugin.yml` — версия → **1.14.14**
- `config.yml` — шапка обновлена с блоком «ЧТО НОВОГО В 1.14.14»

### Миграция и совместимость

- **Миграция конфигов:** не требуется.
- **Миграция данных:** не требуется.
- **Миграция мира/игроков:** не требуется.
- **Breaking changes:** внешние плагины, создававшие `new WarlockAbilities(plugin)` напрямую, сломаются при обновлении (конструктор приватный). Таких плагинов не было — публичный конструктор был только внутренним для `AbilityRegistry`/`TreeAbilities`/`RaskolClasses`.

---

## [1.14.13] — 2026-10-02
### Sprint 9 (Волны 13A–13D)
- **13B:** конструкторы PetService/FoliantService/DotService/CCService без побочных эффектов; регистрация и задачи — из onEnable (activeTasks).
- **13C:** Spec2Points — snapshot-аксессоры; deprecated-поля START_LEVEL/MAX_POINTS/ROW_GATES удалены.
- **13D:** ResourceService — switch по PlayerClass.RegenModel (OCP); overflow-тик Скверны через Hp.max.
- **Selftest:** чек 107 переведён на snapshot-чтение Spec2Points; resetAllDr-опечатка устранена.

---

## [1.14.12] — 2026-09-28
### Sprint 8 (Волны 12A–12C)
- **12A:** снапшот стоп-листа `refreshDisabledCache()`; `hpFractionOf` через Hp.max.
- **12B:** Foliant — isSafeName ПЕРЕД fallback-командой (аудит 7-01); catch сужены до Exception.
- **12C:** HpBarService — warning с ключом конфига вместо пустых catch; symbolOf из метаданных PlayerClass.

---

## [1.14.10] — 2026-10-18

Sprint 7: тестируемость, связность, единая точка чтения конфига.

### Added
- **Unit-тесты (11-01):** JUnit 5 + Surefire 3.2.5; CI переведён на `mvn -B verify` —
  тесты блокируют артефакт при падении. 57 headless-тестов pure-ядра:
  `CombatMath` (19), `DotMath` (14), `ConfigReads` (9), `PetMath` (5),
  `Spec2Node` (5), `Spec2Tree` (5). Якоря сверены с in-game selftest-чеками
  16/62/66/67/68/93/109/111 — unit и in-game проверки не могут разойтись молча.
- **config/ConfigReads** (1-01): единая точка чтения конфига с NaN/Infinity/null-guard
  (`finiteDouble`, `finiteInt`, `stringOr`, `stringNonEmptyOr`, `boolOr`).
- **bootstrap/** (4-01): `StartupChecks` (tmp-чистка, RaskolCore-лог, capacity-warning,
  баннер), `PluginTasks` (purge/autosave-задачи), `SessionListener` (вход/выход игрока).
- **Spec2TreeTest:** инвариант ёмкости [50, 58] по всем 18 деревьям и политика
  disabled-пререквизитов (регресс-страх 6-01) проверяются headless в CI.

### Changed
- **RaskolClasses (4-01):** onEnable сокращён до конструирования слоёв и регистраций;
  startup-проверки, периодические задачи и анонимные слушатели вынесены в bootstrap/*.
  Геттеры-локаторы сохранены (удаление мёртвых — план 1.14.11, аудит 2-01).
- **CombatService (4-03/1-02):** `critFeedback` — единственная публичная точка визуала
  крита; клон из `VanillaDamageListener` удалён. Регистрация `VanillaDamageListener`
  перенесена из конструктора в `onEnable` (конструктор без побочных эффектов).
- **Дубли cfgD/cfgS/cfgI (1-01):** 10 классов (`CombatService`, `VanillaDamageListener`,
  `DamageCaps`, `AbstractKit`, `AttributeService`, `PowerService`, `AvoidanceService`,
  `SchoolImmunity`, `InstallationService`, `Spec2RoleListener`) делегируют в ConfigReads.
  Локальные хелперы с особой семантикой (`baseOf`/`growthOf`, guard v ≥ 0) оставлены
  и помечены в javadoc.
- **AvoidanceService:** удалён неиспользуемый импорт `java.util.Locale` (аудит 2-02).

### Fixed
- Тест-семантика `DotMath.dpsLimit`: cap = 0 — это «кап выключен» (лимит 0 DPS),
  дефолт 6% применяется только при cap < 0 или NaN; расхождение unit-теста и
  реализации устранено фиксом теста, поведение кода не менялось.
- `Spec2TreeTest`: prereq-id больше не хардкодятся — берутся из `node.prereqs()`,
  тест переживает ребаланс деревьев без правок.

### Testing
- Unit: 57 тестов (Surefire, CI-гейт).
- In-game: `/rc selftest` — 122 чека, все PASS.

---

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
- **Spec2Tree**: возвращена двухкомпонентная форма record'а `(id, nodes)` с вычисляемыми `capacity()`/`displayName()` — четырёхкомпонентная редакция первой поставки ломала компиляцию 18 файлов `*Trees.java`.

### Migration
- **Серверы с 1.14.6**: при первом старте 1.14.9 в логе появятся warning'и `Миграция 1.14.8: attributes.avoidance.* → avoidance.*` и `attributes.hp-display.* → hp-display.*`; `config.yml` обновится автоматически. Рекомендуется удалить старые секции вручную после подтверждения миграции.
- **Окно потери при краше**: для кулдаунов/очков путей/ресурсов ≤ `storage.autosave-minutes` (дефолт 5 минут) — приемлемо для короткоживущих данных (та же философия, что P1-7 у HpBarService).

### Testing
- Selftest: 122 чека, все PASS.
- Чек 122: достижимость всех включённых узлов при текущем стоп-листе.

---

## [1.14.8] — 2026-10-13

Архитектурный Sprint 5 (P2): дубли китов, декомпозиция selftest, ревизия стоп-листа.

### Added
- **AbstractKit** (P2-3): общий каркас шести китов способностей — конфиг-хелперы,
  числа с фолбэком `TreeAbilities.*OrKit`, уронное и лечащее ядро с spec2-хуками,
  таргетинг, VFX. Классовый множитель урона — hook `postMult(UUID)`:
  Охотник `true_shot` ×1.30, Разбойник `cold_blood`/`shadow_blades` ×1.5 (до ×2.25),
  Маг `combustion` ×1.25.
- Selftest: чек 121 — стоп-лист `spec2.disabled-nodes` не содержит узлов с живыми
  потребителями (`kit_dur`, `attack_speed_pct`, проки Sprint 3); всего 121 чек.

### Changed
- **P2-1:** `SelftestRunner.run()` декомпозирован на 22 метода по подсистемам;
  состояние отчёта инкапсулировано в держатель `Report` (passed/failed/skipped).
  Номера чеков, порядок вывода и skip-семантика не изменились.
- **P2-3:** шесть китов переведены на `AbstractKit`, удалено ~540 строк дублей.
  Уронное ядро Чернокнижника (`spellDamage`/`tspellDamage` с `WarlockMath.damageMult`)
  осталось китовым: база не знает про Скверну, ад и спек-трейты.
- **P2-4:** стоп-лист `spec2.disabled-nodes` сокращён 38 → 20: ожили 18 узлов
  (5 proc-контрактов, 2 `attack_speed_pct`, 11 `kit_dur`) — потребители реализованы
  в Sprint 3–4. Оставлены только узлы без потребителя в коде.
- **P2-2:** ревизия legacy talents перенесена на 1.15.0 согласно миграционной заметке
  в config.yml; плейсхолдеры `%raskolclasses_talent_*` остаются алиасами spec2-слоя.

### Fixed
- `AbstractKit`: преждевременный терминатор блочного комментария в javadoc первой
  редакции (ошибка компиляции на строке 34) устранён переформулировкой комментария.
- `SelftestRunner.ccProbeCastGuard`: параметр `plugin` добавлен в сигнатуру метода
  и в точку вызова (ошибка компиляции волны 5.1).

---

## [1.14.7] — 2026-10-11

Закрытие аудита 1.14.6 (Sprint 1–4).

### Added
- **P0-6A:** 24 узла ожили: 5 proc-контрактов (`bleed_on_crit`, `burning_extend`,
  `free_heal`, `rage_on_cast`, `enrage_dmg`), 11 узлов `kit_dur` (агрегат длительности
  по цели), `attack_speed_pct`, `deep_wounds`.
- **P0-6A:** реестр `spec2.disabled-nodes` — 34 узла без реализации сделаны no-op
  с прунингом рангов и ждут своих волн.
- **P1-7:** `HpBarService.saveHealth()` пишет в память, на диск данные сбрасываются
  периодически (`storage.health-flush-seconds`, по умолчанию 60 с).
- Selftest: чеки 112–120 (пет-ядро, `spec2.procs`, kit_dur-агрегат, attack-speed-модификатор,
  контракт C `value2`, `HAS_DEPENDENTS`, антиспам-метка, отложенная запись HP, effectiveMax);
  всего 120.

### Changed
- **P1-1/P1-2:** единый путь лечения через `CustomHealEvent`; статический `healerMark`
  удалён, атрибуция целителя — `CustomHealEvent.getHealer()`.
- **P1-5:** `resetNode` отклоняет сброс ранга, от которого зависят купленные узлы
  (`HAS_DEPENDENTS`).
- **P1-6:** антиспам-метка каста в `AbilityRegistry.castOn` (чеки 117–118).
- **P1-8:** шкала ресурса нормируется на `ResourceState.getEffectiveMax()`, а не на
  константу 100; узлы `resource_max` и `setCeiling` растягивают бар выше 100.
- Docs: README пересобран (баннер, бейджи, ровные таблицы, 1.14.7 и 120 чеков);
  описание в `plugin.yml` сокращено до одной строки.

### Fixed
- **P0-2:** proc-узлы ожили — `Spec2Service.accumulate` срезает префикс `proc_`
  (ключ агрегата `riposte`, а не `proc_riposte`); 31 узел снова читается ProcService.
- **P0-3:** контракт единиц проков — значение узла = доля шанса за ранг (бросок),
  сила = `spec2.procs.<id>.amp` (новые ключи конфига, дефолты из описаний узлов).
- **P0-7:** секции `avoidance:` и `hp-display:` перенесены в корень config.yml
  (код читает их с корня); `parry-k` унифицирован до 300; добавлены ключи
  `combat.block.visuals/sound`, ранее жившие только дефолтами кода.
- **P0-8a:** повторный призыв пета (ALREADY) возвращает false — ресурс и кулдаун
  больше не сгорают на отказе.
- **P0-8d:** релог до применения отложенного restore не перезаписывает `health.yml`
  неподтверждённым ratio.
- **P0-8f:** PetService логирует warning, если атрибуты реестра не резолвились;
  добавлен `attributesReady()` для selftest.
- Docs: README — возвращены баннер и бейджи, счётчик selftest 111; CHANGELOG —
  история зафиксирована одним каноническим набором дат.

---

## [1.14.6] — 2026-10-09

### Added
- **Пет-система** (`pet/*`): PetDef (wolf/demon/shadowfiend), PetMath (pure scaling
  hp = base + attr×2, dmg = base + power×0.3 × pet_pct-узлы), PetService
  (жизненный цикл, follow 12 блоков, ttl, баффы, consume, гейты canHit, ретаргет).
- Киты переведены на PetService: `petWolf/beastFerocity/bestialWrath` (Охотник),
  `summonDemon/demonSoul` (Чернокнижник), `shadowfiend` (Жрец); ad-hoc карты
  PET_WOLF/DEMON_BY_OWNER и inline-Vex удалены.
- Selftest: чеки 108–111 (PetDef-реестр, PetMath, имена, ttl/teleport).

### Fixed
- Paper 1.21.4: `org.bukkit.attribute.Attribute` (не `org.bukkit.Attribute`);
  спавн через `World.spawnEntity` + `instanceof LivingEntity` (wildcard
  `getEntityClass()` ломал generic-вывод `World.spawn`).

---

## [1.14.5] — 2026-10-08 (hotfix)
- Хотфикс: `KitSanity.validatePassiveMults` в selftest; RUNBOOK-пассивки сверены с китами.

---

## [1.14.4] — 2026-10-08

Закрытие аудита П1–П10 волнами 1–4 (внутренние метки 1.14.1–1.14.3).

### Added
- Волна 1: ROW_LINE_SLOTS 9 (видны ряды 4–6 и ульты), переключатель спек в книге,
  роль HEALER через `CustomHealEvent`, `hpBarService.saveAll()` в onDisable.
- Волна 2: централизованный гейт древесных способностей в `AbilityRegistry.castOn`;
  чек 99 (unlock_ability ↔ кастеры); фикс таргета `su_cloak_of_shadows`.
- Волна 3: геттеры агрегата (magic_dmg_pct и семейство proc-видов); хот-путь
  урона/критов/паверов/ресурса/атрибутов; `ProcService` (мили- и каст-проки);
  `AvoidanceService.tryAvoid` → AvoidResult; блок щитом = PARRY + щит;
  чек-композиция лечения в одной точке (убран двойной счёт роли HEALER).
- Волна 4: `resetNode` (респец ранга узла, цена 150 + 50×ранг, ПКМ×2 с взводом);
  resist-узлы школ → ElementalResistService; `Spec2Points.configure` (start-level/
  max-points/row-gates из конфига); хранилище `spec2-storage.yml` с миграцией.
- Selftest: чеки 100–107.

### Changed
- Amplifier proc-узлов (riposte/counterattack/revenge/shield_slam) ставится на
  защищавшегося, а не на атакующего.

---

## [1.14.3] — 2026-10-06
### Боевые проки и процентные агрегаты (Волна 3)
- 3A/3B: phys_dmg_pct/magic_dmg_pct/block_pct/crit_melee_pct в хот-пути урона.
- 3C1/3C2: ProcService — мили- и каст-проки (bleed_on_crit, double_strike, riposte,
  revenge, shield_slam, second_wind, expose, stealth_bonus, armor_pen, reflect_magic).

---

## [1.14.2] — 2026-10-05 (hotfix)
- Хотфикс reconcile spec2 при смене мира; purge карт китов по расписанию.

---

## [1.14.1] — 2026-10-05
- HpBarService.saveAll() в onDisable — здоровье не теряется при краше/остановке.

---

## [1.14.0] — 2026-10-05

### Added
- Деревья путей «Спек 2.0»: 18 специализаций, 46 очков (15→60), гейты рядов,
  роли FIGHTER/TANK/HEALER, 69 древесных способностей, DoT wither (SHADOW).
- Переносимые китовые slots 4–5 → treeAbilities (Б11.1.x); legacy-слой talent/spec удалён.
- Selftest: чеки 91–99.

---

## [1.13.0] — 2026-10-01

### Added
- Контроль и убывающая отдача: CCService/CCGuard/CastGuard, 9 типов CC, 6 категорий DR,
  CastChannels, VanillaCCWrapper, голограммы TextDisplay, `/rc cc *`.
- Selftest: чеки 76–90.

---

## [1.12.0] — 2026-09-25

### Added
- Школы урона (8 школ → 3 канала), митигация/пробитие/иммунитеты, стихийный резист,
  PenTraitsService; DoT-ядро (burning/poison/bleed/chilled), кап DoT-DPS 6%;
  BalanceSimulator.matrix 6×6.
- Selftest: чеки 49–75.

---

## [1.11.0] — 2026-09-20

### Added
- Книга класса (5 вкладок), инсталляции (6 типов incl. Пентаграмма),
  хотбар-бинды и свитки постановки.

---

## [1.10.0] — 2026-09-15

### Added
- Шестой класс — Чернокнижник: Фолиант Душ (дроп 0.001%, soulbound, анти-фарм S2),
  Скверна (пороги 75/100, откат 6.66%, ×6 в аду), спек-трейты.

---

## [1.9.0] — 2026-09-10

### Added
- План B по HP: carrier ≤ 1024, formula без потолка, HpPool — единые точки входа;
  burst-окно 3 с ≤ 18%, анти-ваншот ≤ 35%, LOS для площадей.

---

## [1.8.0] — 2026-09-05

### Added
- RaskolGear-интеграция: резисты шмота, сет-бонусы 4/4, +HP в HpPool, pen-трейты,
  `/rc gear`, вкладка книги «Шмот и сеты».

---

## [1.7.0] — 2026-09-01

### Added
- Первый релиз: 5 классов, 25 китовых способностей, ресурсы 0–100 с боевым окном,
  кулдауны, активные эффекты, HUD actionbar, атрибуты STR/AGI/INT, WP/SP/HPow,
  уклонение/парирование с DR, криты.

---

[Unreleased]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.8...HEAD
[1.15.8]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.6...v1.15.8
[1.15.6]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.5...v1.15.6
[1.15.5]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.4...v1.15.5
[1.15.4]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.3...v1.15.4
[1.15.3]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.2...v1.15.3
[1.15.2]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.1...v1.15.2
[1.15.1]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.15.0...v1.15.1
[1.15.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.16...v1.15.0
[1.14.16]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.15...v1.14.16
[1.14.15]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.14...v1.14.15
[1.14.14]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.13...v1.14.14
[1.14.13]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.12...v1.14.13
[1.14.12]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.10...v1.14.12
[1.14.10]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.9...v1.14.10
[1.14.9]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.8...v1.14.9
[1.14.8]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.7...v1.14.8
[1.14.7]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.6...v1.14.7
[1.14.6]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.5...v1.14.6
[1.14.5]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.4...v1.14.5
[1.14.4]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.3...v1.14.4
[1.14.3]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.2...v1.14.3
[1.14.2]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.1...v1.14.2
[1.14.1]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.14.0...v1.14.1
[1.14.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.13.0...v1.14.0
[1.13.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.12.0...v1.13.0
[1.12.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.11.0...v1.12.0
[1.11.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.10.0...v1.11.0
[1.10.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.9.0...v1.10.0
[1.9.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.8.0...v1.9.0
[1.8.0]: https://github.com/hayferdahmer/RaskolClasses/compare/v1.7.0...v1.8.0
[1.7.0]: https://github.com/hayferdahmer/RaskolClasses/releases/tag/v1.7.0
