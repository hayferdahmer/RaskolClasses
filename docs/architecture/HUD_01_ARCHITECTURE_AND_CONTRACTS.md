# HUD-01 — архитектура и контракты RaskolHUD

**Статус:** source-backed audit и целевая архитектура; реализация не начата  
**Ветка:** `refactor/a03-foundation-lifecycle`  
**Платформа:** Paper 26.2 / Java 25  
**Область:** только presentation HUD и его runtime-контракты. Игровые формулы, баланс, экономика и поведение способностей не меняются.

## 1. Цель этапа

Подготовить безопасную основу для HUD-02: отделить подготовку отображаемых данных, владение runtime-задачами и вывод в Paper UI от игровых сервисов. Сохранить существующие визуальные режимы и совместимость конфигурации до отдельного решения о миграции.

Этот документ фиксирует наблюдаемое поведение текущей ветки и целевые контракты. Он не объявляет новую архитектуру реализованной.

## 2. Текущая инвентаризация

### 2.1 ActionBar: `HudService`

Файл: `src/main/java/dev/raskol/classes/hud/HudService.java`

Текущие обязанности:
- формирование классового ресурсного бара и DoT-строки;
- применение class theme, иконок, цветов и анимации восстановления ресурса;
- пропуск невидимых игроков, spectator (если включено) и игроков без класса;
- dirty-rendering по ключу кадра, с периодическим повтором статичного кадра;
- хранение per-player overrides и кэшей последнего кадра.

Важные наблюдения:
- `tick()` обходит всех online players и читает игровые сервисы напрямую;
- `tickFor()` вызывает `Player.sendActionBar`;
- при полном ресурсе может создавать particle в мире;
- `clearPlayer/clearAll` очищают внутреннее состояние и не отправляют пустой ActionBar;
- планирование вынесено в `HudRuntimeOwner`, Paper scheduler адаптирован через `PaperHudTaskScheduler`.

### 2.2 ActionBar: `HpBarService`

Файл: `src/main/java/dev/raskol/classes/hud/HpBarService.java`

Это смешанный сервис, и его нельзя целиком переносить в presentation:
- игровая логика: max HP, регенерация от STR, применение атрибутов и лечение;
- lifecycle: join/quit/respawn, отложенное восстановление HP;
- persistence: `health.yml`, dirty state и асинхронный flush;
- presentation: health scaling и unified ActionBar HP + resource.

Режим `hp-display.mode` определяет вывод:
- при значении `vanilla` HP ActionBar не отправляется, vanilla health display используется без health scaling; отдельно запускается классовый `HudService`, если `hud.enabled=true`;
- в остальных режимах `HpBarService` включает health scaling и отправляет unified ActionBar с HP и ресурсом; классовый `HudService` не запускается.

Таким образом, два основных persistent ActionBar сейчас взаимно исключаются выбором режима. Это важная legacy-семантика, которую нельзя случайно сломать при декомпозиции.

### 2.3 BossBar: `BossBarService`

Файл: `src/main/java/dev/raskol/classes/hud/BossBarService.java`

Отдельный вывод активных эффектов:
- читает ActiveEffectManager и class theme;
- выбирает эффекты по минимальной длительности и ограничению числа видимых полос;
- создаёт, обновляет и удаляет Bukkit BossBar;
- скрывает полосы игрока, если ActionBar HUD невидим, класс не определён или игрок вышел.

Периодическая работа принадлежит `BossBarRuntimeOwner`; Paper scheduler — `PaperBossBarTaskScheduler`. BossBar не должен сливаться с ActionBar arbitration: это отдельный UI-канал и отдельный жизненный цикл.

### 2.4 Временные ActionBar-сообщения

`CooldownManager` также вызывает `Player.sendActionBar` при завершении достаточно длинного кулдауна. Это transient notification, не принадлежащее persistent HUD-кадру.

Следовательно, в текущей системе как минимум три независимых владельца могут отправлять ActionBar:
1. `HudService` — классовый ресурс + DoT;
2. `HpBarService` — HP + ресурс;
3. `CooldownManager` — краткое уведомление о готовности способности.

Это потенциальная конкуренция за один клиентский канал. Последняя отправка заменяет предыдущий ActionBar; отдельного общего арбитра/очереди в рассмотренных путях нет.

### 2.5 Runtime ownership и lifecycle

Текущие owners:
- `HudRuntimeOwner`: синхронный периодический tick + quit cleanup;
- `HpBarRuntimeOwner`: регистрация join/quit/respawn listener и синхронный tick;
- `BossBarRuntimeOwner`: периодический producer + очистка полос.

В `RaskolClasses` они создаются в `initializeCoreServices`, запускаются в `startRuntimeTasks`, закрываются через `ShutdownStepRunner`. HP runtime запускается независимо от режима отображения, поскольку обслуживает не только визуальный вывод, но и HP lifecycle/gameplay. ActionBar HUD запускается только при `hud.enabled && hp-display.mode == vanilla`. BossBar owner запускается независимо; сам сервис проверяет `hud.boss-bar.enabled`.

При `/rc reload`:
- `HudService.applyConfig()` сбрасывает его render caches;
- `reconcileHudRuntime()` запускает или останавливает `HudRuntimeOwner`;
- `BossBarRuntimeOwner.restart()` пересоздаёт periodic task и очищает бары;
- HP runtime owner не перезапускается, а HP service читает текущие значения конфигурации в tick.

### 2.6 PlaceholderAPI

`PlaceholderSnapshotService` — внешний data integration, а не renderer HUD. Он публикует immutable-by-convention snapshots по UUID для PlaceholderAPI и должен остаться отдельным потребителем доменных данных. Не включать PlaceholderAPI в общий HUD render loop и не делать его обязательной зависимостью HUD.

## 3. Подтверждённые архитектурные риски

| ID | Наблюдение | Риск / требование |
|---|---|---|
| HUD-R01 | Три независимых пути отправки ActionBar | Transient cooldown может перезаписать persistent HUD; последующий HUD tick может тут же вернуть свой кадр. Нужен единый канал публикации/арбитража. |
| HUD-R02 | `HpBarService` объединяет gameplay, persistence, lifecycle и presentation | Нельзя переносить или переписывать сервис целиком ради HUD. Нужна постепенная декомпозиция с сохранением публичного фасада/поведения. |
| HUD-R03 | `HudService` и `HpBarService` оба формируют persistent ActionBar в разных режимах | Нужна явная модель режима/слота, чтобы не запускать одновременно два persistent producer. |
| HUD-R04 | BossBar зависит от `HudService.isVisible(player)` | При режиме `hp-display.mode != vanilla` HUD owner не запускается, но BossBar продолжает работать. Видимость BossBar тогда зависит от конфигурационного/override состояния HudService, а не от активного ActionBar producer. Семантику нужно зафиксировать отдельно. |
| HUD-R05 | ActionBar и BossBar читают игровые сервисы непосредственно | Сильная связанность presentation с plugin facade и игровыми сервисами; тестирование и замена renderer затруднены. |
| HUD-R06 | `HudService` делает particle side effect при полном ресурсе | Particle — визуальный эффект, но не часть отправки ActionBar. Не потерять его при выделении renderer; отдельно определить owner/feature gate. |
| HUD-R07 | Конфигурация содержит legacy-параметры в разных секциях | Нужны compatibility mapping и тесты reload; не переименовывать ключи без миграции. |

## 4. Целевая модель

### 4.1 Слои

1. **Gameplay/domain services** — остаются владельцами истины: HP, ресурсы, класс, эффекты, DoT, кулдауны.
2. **HUD data providers** — на Paper main thread собирают минимальные снимки для отображения. Снимки immutable, содержат UUID и только примитивные/текстовые presentation values; не передают Player, World, Entity, Bukkit BossBar или mutable domain collections.
3. **Presentation models** — описывают готовое к рендерингу состояние: resource gauge, HP gauge, DoT entries, active-effect entries, transient notification.
4. **Renderers / channel publishers** — единственные компоненты, которым разрешено отправлять ActionBar или управлять BossBar. Все Bukkit/Paper UI операции выполняются на main thread.
5. **Runtime owners** — владеют только расписанием, регистрациями и cleanup. Они не содержат gameplay rules и не принимают решений о балансе.

### 4.2 Каналы вывода

**ActionBar channel**
- один publisher на игрока;
- persistent layer: ровно один выбранный HUD-профиль (legacy class-resource+DoT или unified HP+resource);
- transient layer: cooldown и другие краткоживущие уведомления;
- приоритет, TTL, повторная публикация и возврат persistent frame должны быть явными, детерминированными и покрыты тестами;
- отправка одинакового persistent frame не должна создавать лишний сетевой трафик;
- transient message не должен навсегда вытеснять persistent HUD.

**BossBar channel**
- отдельный publisher и отдельные сущности/handle;
- данные поступают через snapshot provider;
- существующие правила отбора эффектов, цветов, форматов и max-visible сохраняются;
- cleanup выполняется на quit, disable, отключении функции и смене режима, если это предусмотрено контрактом.

**Vanilla health display**
- остаётся режимом отображения HP, а не третьим конкурирующим ActionBar publisher;
- HP gameplay и persistence не зависят от выбранного renderer.

### 4.3 Владение и потоки

- Чтение Bukkit Player/World/Entity и создание snapshot — только Paper main thread.
- Рендеринг ActionBar/BossBar — только Paper main thread.
- В асинхронные callback не передавать Bukkit Player; использовать UUID и заново разрешать текущую сессию на main thread.
- Per-player presentation state индексируется UUID и очищается на quit.
- Каждый periodic producer имеет ровно одного runtime owner; повторный start/reload/close должен иметь документированную идемпотентность.
- Отключение producer выполняется до очистки принадлежащего ему UI state.
- Ошибка одного renderer/cleanup не должна блокировать очистку остальных каналов.

## 5. Контракты данных — целевые

Предварительные интерфейсы (названия illustrative; до HUD-02 могут быть уточнены после тест-дизайна):

- `HudSnapshotProvider`: формирует снимок для UUID/Player на main thread.
- `HudSnapshot`: неизменяемый набор отображаемых значений; без Bukkit объектов и ссылок на сервисы.
- `ActionBarPublisher`: принимает persistent frame или transient notification и отвечает за арбитраж/TTL.
- `ActionBarRenderer`: преобразует presentation model в Adventure Component; не читает игровые сервисы.
- `BossBarSnapshotProvider`: формирует список активных эффектов для игрока.
- `BossBarRenderer`: применяет snapshot к Paper BossBar и очищает устаревшие полосы.
- `HudRuntimeOwner`: запускает обновление snapshots/publishers, регистрирует session cleanup и владеет task handles.

Не вводить общий универсальный event bus. Контракты должны быть узкими, типизированными и отражать конкретные каналы.

## 6. Политика совместимости

HUD-02 должен сохранить до отдельного решения:
- текущие config keys `hud.*`, `hp-display.*`, `messages.hud.*`, `hud.boss-bar.*`;
- текущие default values и fallback поведения;
- режим `hp-display.mode=vanilla` и unified ActionBar mode;
- классовые темы, символы, DoT иконки/форматирование, шкалы и regen spark;
- per-player HUD visibility override;
- cooldown-ready notification и его звуковое сопровождение;
- правила boss-bar filtering и cleanup;
- PlaceholderAPI snapshots как отдельный интеграционный путь.

Любая смена поведения, внешнего конфига или визуального приоритета требует отдельного решения пользователя и миграционного плана.

## 7. План HUD-02

1. Добавить pure presentation models и unit tests без Bukkit dependency.
2. Реализовать ActionBar arbitration с тестами приоритета, TTL, reconnect/quit, повторной публикации и отключения.
3. Перевести transient cooldown notification на publisher, сохранив sound timing и session safety.
4. Перевести текущий persistent renderer (`HudService` или unified HP/resource) на snapshot contract без изменения визуального результата.
5. Выделить presentation facade из `HpBarService`, оставив gameplay, health persistence и lifecycle в текущем владельце до отдельного этапа.
6. Перевести BossBar на snapshots и renderer; сохранить BossBarRuntimeOwner ownership.
7. Проверить config reload, disabled modes, quit/rejoin, shutdown и все комбинации двух HP modes.
8. Провести staging smoke test на Paper 26.2 / Java 25.

## 8. Обязательные тестовые сценарии

- `hud.enabled` true/false × `hp-display.mode` vanilla/actionbar/unknown;
- persistent ActionBar остаётся единственным независимо от порядка вызова producer;
- transient cooldown появляется, истекает по TTL, после чего persistent frame восстанавливается;
- несколько transient notifications разрешаются детерминированно;
- идентичный persistent frame не отправляется повторно без необходимости;
- player quit очищает per-player snapshots, channel leases и BossBars;
- reconnect не получает stale callback от старой сессии;
- BossBar enabled/disabled, max-visible, min-duration, исчезновение эффекта и смена класса;
- reload не создаёт duplicate tasks и не оставляет старые BossBars;
- shutdown останавливает producers до очистки UI;
- исключение одного cleanup не пропускает другие cleanup;
- все Bukkit API обращения происходят на Paper main thread;
- HP gameplay, health.yml dirty flush и respawn restore не меняются от выбора renderer.

## 9. Вне области

- изменение формул HP/ресурса, регенерации, эффектов и DoT;
- изменение кулдаунов и баланса способностей;
- новый дизайн HUD, новые элементы/анимации и новые конфигурационные возможности;
- переписывание PlaceholderAPI;
- включение `BootstrapCoordinator`;
- merge в `main`.

## 10. Gate и критерии завершения HUD-01

HUD-01 можно считать завершённым после:
- source-backed inventory зафиксирована;
- ActionBar conflict и HP mode semantics описаны;
- data, renderer, channel и runtime ownership contracts определены;
- compatibility constraints и test matrix утверждены;
- HUD-02 scope отделён от gameplay changes.

На момент создания документа это архитектурный baseline, а не результат реализации или CI. Отдельный A-04 live smoke test на staging Paper 26.2 / Java 25 остаётся открытым и не считается пройденным данным документом.
