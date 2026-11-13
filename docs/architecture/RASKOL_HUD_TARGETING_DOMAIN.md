# RaskolHUD — HUD-C.1.5 Targeting Domain & Application Contracts

## Статус

**Domain contracts, in-memory selection store и TargetingApplicationService реализованы; concrete policies и Paper adapter ещё не реализованы.**

- Repository: `hayferdahmer/RaskolClasses`
- Branch: `refactor/a03-foundation-lifecycle`
- Platform: Paper 26.2 / Java 25
- Decisions: H-05 Locked Target; H-06 ПКМ по допустимой цели
- Parent: [RASKOL_HUD_ARCHITECTURE.md](RASKOL_HUD_ARCHITECTURE.md)
- Data contracts: [RASKOL_HUD_DATA_CONTRACTS.md](RASKOL_HUD_DATA_CONTRACTS.md)
- Paper API spike: [RASKOL_HUD_TARGET_INTERACTION_SPIKE.md](RASKOL_HUD_TARGET_INTERACTION_SPIKE.md)
- Runtime ownership: [RASKOL_HUD_RUNTIME_OWNERSHIP.md](RASKOL_HUD_RUNTIME_OWNERSHIP.md)

Этот документ фиксирует границы Targeting Domain, application-команд, read-модели и политик допуска. Это не реализация и не утверждение, что выбранная цель уже отображается в HUD.

## 1. Обязанности и границы

Targeting — самостоятельная игровая подсистема. Она не является частью RaskolHUD.

Targeting отвечает за:
- явный выбор и снятие выбранной цели;
- хранение выбранной цели для активной игровой сессии;
- проверку допустимости выбора;
- проверку актуальности выбранной цели;
- очистку состояния при завершении сессии игрока;
- публикацию read-only проекции для HUD и других разрешённых потребителей.

RaskolHUD отвечает только за чтение TargetView и его отображение. Он не подписывается на Paper interaction event напрямую, не выбирает цель и не меняет её.

Targeting не отвечает за:
- боевую механику, нанесение урона или агро;
- автоматическое наведение по crosshair/raycast;
- глобальное glowing-состояние сущности;
- управление инвентарём, экипировкой или NPC-диалогами;
- хранение Bukkit Entity/Player ссылок;
- постоянное сохранение выбранной цели между перезапусками.

## 2. Слои и зависимости

```text
Paper PlayerInteractAtEntityEvent
          |
          v
PaperTargetInteractionAdapter
          | TargetInteractionInput (UUIDs + source)
          v
TargetingApplicationService
          |
          +--> TargetSelectionPolicy
          |      +--> ActorSessionPort
          |      +--> TargetCandidatePort
          |      +--> TargetRangePolicy
          |      +--> TargetCombatPolicy
          |      +--> TargetTerritoryPolicy
          |      +--> TargetPermissionPolicy
          |      +--> NpcClassificationPort
          |
          +--> TargetSelectionStore
          |
          +--> TargetingEventPublisher
          |
          v
TargetHudReadPort --> immutable TargetReadResult / TargetView
          |
          v
HudSnapshotProvider --> PlayerHudSnapshot
```

Правила зависимостей:
- Domain-контракты не импортируют Bukkit/Paper, Adventure, Citizens, Towny или RaskolZones.
- Paper adapter преобразует Bukkit event в application input и передаёт UUID, а не объекты Bukkit.
- Application layer оркестрирует policy ports и store; он не содержит Bukkit-вызовов.
- Infrastructure предоставляет Paper entity/session resolvers и optional integration adapters.
- HUD зависит только от read-port контракта, а не от TargetingApplicationService или store.
- Интеграции RaskolZones, PvP и NPC подключаются через ports; отсутствие optional adapter не должно ломать загрузку плагина.

## 3. Основные value contracts

### 3.1 TargetInteractionInput

Вход от Paper adapter:
- actorId: UUID;
- candidateId: UUID;
- interactionSource: enum, первоначально ENTITY_RIGHT_CLICK;
- hand: enum, первоначально MAIN_HAND;
- observedAtMillis: long.

Input не содержит Player, Entity, Location, World или Event. Значение hand повторно проверяется на границе application, даже если adapter уже фильтрует off-hand.

### 3.2 TargetIdentity

Минимальная стабильная идентичность:
- entityId: UUID.

Не сохранять EntityType как единственный идентификатор, Bukkit Entity, имя, координаты или chunk reference. Категория и отображаемое имя — разрешаемые свойства, а не identity.

### 3.3 TargetSelection

Эфемерное состояние выбранной цели:
- ownerId: UUID;
- targetId: UUID;
- selectedAtMillis: long;
- source: ENTITY_RIGHT_CLICK.

В store хранится только identity и минимальные метаданные выбора. Никаких live entity references и сериализации в character profile. Selection сбрасывается при завершении игровой сессии игрока.

### 3.4 TargetView

Immutable read model для HUD:
- targetId: UUID;
- category: enum (LIVING, PLAYER, NPC, BOSS, OTHER_SUPPORTED);
- displayName: безопасный plain-text display value;
- level: optional value + availability;
- currentHealth / maxHealth: optional values + availability;
- normalizedHealth: optional 0..1;
- relation/status tags: только если они предоставлены авторитетным источником;
- selectedAtMillis;
- capturedAtMillis.

В TargetView запрещены Bukkit объекты, mutable компоненты, произвольные plugin objects и ссылки на внутренние кэши. Отсутствующие данные не превращаются в ноль или пустую строку без явной семантики.

## 4. Результаты чтения и отказоустойчивость

Read-port возвращает discriminated result со статусом:

- NONE — запрос успешно выполнен, выбранной цели нет;
- AVAILABLE — цель выбрана и необходимые для базового Target Frame данные разрешены;
- UNAVAILABLE — источник или resolver временно не смог достоверно определить состояние.

UNAVAILABLE не эквивалентен NONE. При временной ошибке resolver нельзя молча удалять выбор игрока. Последняя известная цель может быть сохранена как identity внутри targeting store, но HUD не должен выдавать устаревшие HP/name за актуальные: он получает UNAVAILABLE с безопасной диагностической категорией и временем последней успешной проверки, если это полезно.

Подтверждённая невалидность цели — отдельный результат validation, после которого selection очищается и следующий read возвращает NONE. Ошибка интеграции, таймаут или временная недоступность мира не считаются доказательством невалидности.

Ошибки отдельных optional полей не должны делать недоступным весь TargetView: например, если уровень неизвестен, но имя и HP доступны, базовый target frame остаётся AVAILABLE с level=UNAVAILABLE.

## 5. Application operations

Внутренний минимальный application API:

- select(TargetInteractionInput) — проверяет и фиксирует новую цель;
- clear(actorId, reason) — снимает текущую цель;
- selectedTargetOf(actorId) — read-only query;
- validateSelectedTargets(boundedBatch) — проверяет актуальность ограниченной партии selection;
- endSession(actorId) — очищает selection и связанные transient state.

Повторный выбор уже выбранной цели идемпотентен: он не создаёт дубликаты, но может обновить selectedAtMillis только если продуктовая семантика явно требует этого. Базовое поведение — сохранить исходный timestamp при повторном клике на ту же цель.

Команды cycle/nearest и конкретные slash-команды не утверждаются этим этапом. Они требуют отдельного UX-решения и не должны появляться как неявная часть минимального API.

## 6. Selection policy и порядок проверок

Policy должна возвращать типизированное решение (ACCEPT / REJECT / UNAVAILABLE) и внутреннюю причину отказа. Не отправлять пользователю необработанные exception messages или внутренние имена интеграций.

Порядок проверок:
1. Input корректен; actor и candidate UUID не null и не совпадают.
2. Actor session существует, активна, игрок online, жив и не spectator.
3. Candidate разрешается и остаётся valid/alive/targetable.
4. Candidate классифицирован как допустимая цель; armor stand, decoration, item frame и protected utility entity отклоняются.
5. Actor и target находятся в одном мире; дистанция не превышает конфигурируемый предел.
6. NPC classification определяет, является ли сущность dialogue/decorative/combat NPC. Неизвестная NPC-метка не должна автоматически трактоваться как разрешение на атаку.
7. Permission/class policy допускает выбор.
8. PvP policy допускает player-vs-player выбор в текущем контексте.
9. Territory/safe-zone policy допускает выбор в текущем контексте.
10. Только после всех обязательных ACCEPT store заменяет selection.

Если обязательный policy provider недоступен, результат UNAVAILABLE, а не ACCEPT. Optional provider может иметь явно утверждённый безопасный fallback; этот fallback должен быть задокументирован для каждой интеграции, а не подразумеваться общим правилом.

## 7. NPC, PvP и territory integration seams

### NPC

NpcClassificationPort должен возвращать как минимум:
- NOT_NPC;
- TARGETABLE_COMBAT_NPC;
- NON_TARGETABLE_NPC;
- UNKNOWN.

Не выводить NPC статус только из EntityType или display name. Citizens и другие NPC-плагины остаются optional. UNKNOWN не даёт автоматического разрешения на выбор, если сущность помечена известным NPC provider.

### PvP / combat

TargetCombatPolicy отвечает за допустимость выбора игрока как цели в текущем PvP/PvE контексте. Выбор цели сам по себе не запускает бой и не даёт права атаковать; боевые проверки остаются в Combat/World Interaction.

### Territory

TargetTerritoryPolicy — узкий read-only port. В перспективе его может реализовать адаптер RaskolZones. Targeting не зависит от классов RaskolZones и не дублирует иерархию территориальных правил. Решение «можно ли выбрать» и решение «можно ли атаковать» должны оставаться разными политиками, если игровые правила не определят их как одно и то же.

## 8. Lifecycle и invalidation

Selection очищается при:
- actor quit / session end;
- actor death, если игровая политика не предусматривает сохранение target после смерти;
- подтверждённой смерти/удалении/невалидности target;
- смене мира actor или target;
- подтверждённом выходе target за допустимое состояние/доступность, если это определено policy.

Временная ошибка чтения, кратковременный chunk unload или отказ optional integration не должны автоматически очищать selection. Требуется различать подтверждённую invalidation и невозможность проверить состояние.

Проверка выполняется по событиям там, где они надёжны, плюс ограниченная пакетная проверка выбранных целей. Не запускать полный обход всех сущностей или всех игроков каждый tick. Проверка при чтении TargetView может выступать дополнительной защитой, но не должна приводить к неограниченной синхронной работе.

При завершении сессии Targeting очищает только собственное состояние. HUD отдельно очищает свои snapshots/render handles через HudSessionCleanup.

## 9. Target frame presentation contract

Target Frame получает:
- NONE: скрывает target frame или показывает согласованное пустое состояние;
- AVAILABLE: показывает актуальные поля TargetView;
- UNAVAILABLE: сохраняет визуальное различие «данные временно недоступны» и не подменяет его отсутствием цели.

Target Frame не управляет selection. Клик по панели, crosshair movement, raycast preview и world outline не меняют выбранную цель. World outline/glowing не входит в этот контракт: это отдельная presentation capability с риском конфликтов с другими плагинами и требует отдельного исследования.

Поскольку Paper-only клиент не предоставляет произвольный экранный canvas, TargetView остаётся независимым от конкретного renderer. Клиентский мод в будущем может отобразить тот же контракт в полноценной WoW-подобной рамке без изменения targeting domain.

## 10. Required tests

### Domain/application
- valid right-click selects target;
- repeated selection is idempotent;
- invalid actor/session, dead actor and spectator reject;
- self, dead, invalid, non-living and protected utility entities reject;
- armor stand and decorative entity reject;
- cross-world and out-of-range candidates reject;
- policy rejection does not replace existing target;
- required provider failure returns UNAVAILABLE and does not accept selection;
- NPC classifications are handled explicitly;
- PvP and territory policies are enforced independently;
- temporary resolver failure preserves selection identity;
- confirmed target invalidation clears selection;
- actor session end clears only actor-owned selection;
- no crosshair/raycast input can mutate selection.

### Read model
- NONE differs from UNAVAILABLE;
- missing optional level does not hide otherwise valid target;
- mutable source values are copied;
- TargetView contains no Bukkit objects;
- normalized health is clamped and handles maxHealth <= 0 safely;
- returned collections/metadata are immutable.

### Paper adapter
Use the HUD-C.1.4 matrix: main hand, non-cancelled event, MONITOR, no event mutation, no duplicate base/subclass listener. Add a controlled Paper 26.2 test with actual deployed NPC plugins before production activation.

## 11. Implementation sequence

1. Add Bukkit-independent targeting value types and ports.
2. Implement in-memory TargetSelectionStore with deterministic tests.
3. Implement TargetingApplicationService and policy composition using fakes.
4. Add Paper candidate/session resolver adapters and the selected interaction listener.
5. Add session/death/world invalidation and bounded validation.
6. Add TargetHudReadPort adapter consumed by HudSnapshotProvider.
7. Run unit tests, Maven verify, Spotless, SpotBugs and controlled Paper 26.2/NPC integration test.
8. Only then wire the feature into production bootstrap behind validated configuration.

Do not combine all steps in one commit. Keep the targeting domain independent from the HUD renderer and preserve the existing H-05/H-06 decisions.

## 12. HUD-C.1.5 — Domain value contracts implemented

Добавлены Bukkit-независимые типы в `dev.raskol.classes.targeting.domain`:
- `TargetIdentity` — UUID-идентичность цели;
- `TargetSelection` и `TargetSelectionSource` — эфемерный факт выбора;
- `TargetCategory` — категория сущности для read-модели;
- `TargetView` — immutable snapshot с optional level/health, immutable status tags и безопасным normalized health;
- `TargetReadResult` — отдельные `None`, `Available` и `Unavailable` варианты;
- `TargetUnavailableReason` — безопасные диагностические категории.

`TargetDomainContractsTest` покрывает null/invalid input, копирование коллекций, нормализацию здоровья, optional values и различие NONE/UNAVAILABLE. Типы не импортируют Bukkit/Paper API.

Добавлен `TargetSelectionStore` application port и потокобезопасный `InMemoryTargetSelectionStore` infrastructure adapter на `ConcurrentHashMap`. Хранилище поддерживает поиск по owner UUID, замену selection, удаление одной сессии и очистку всего transient state. `InMemoryTargetSelectionStoreTest` покрывает изоляцию владельцев, replace/remove/clear и null input.

Добавлены application contracts:
- `TargetInteractionInput` — UUID-based вход без Bukkit types;
- `TargetSelectionPolicy` — порт композиции проверок actor/candidate/range/permission/PvP/territory/NPC;
- `TargetSelectionPolicyResult` — ACCEPTED / REJECTED / UNAVAILABLE;
- `TargetSelectionOutcome` — SELECTED / ALREADY_SELECTED / REJECTED / UNAVAILABLE;
- `TargetingApplicationService` — select, clear, selectedTargetOf и endSession.

Сервис отклоняет self-target и off-hand до вызова policy, выполняет policy до изменения store, не заменяет предыдущую цель при REJECTED/UNAVAILABLE, а повторный выбор той же цели после успешной проверки идемпотентен и сохраняет исходный timestamp. Clock передаётся как зависимость. Методы синхронизированы для защиты от конкурирующих вызовов одного экземпляра; Paper adapter всё равно обязан соблюдать main-thread discipline.

`TargetingApplicationServiceTest` покрывает принятие, повторный выбор, повторную проверку, отказ/недоступность без замены, self-target, off-hand, очистку сессии, некорректные часы и null dependencies.

Пока отсутствуют concrete policy composition, Paper listener и production wiring. Maven/Spotless/SpotBugs CI для этих изменений ещё не подтверждён.

## 13. HUD-C.1.6 — Selection store implemented

Реализован `TargetSelectionStore` и in-memory adapter. Store индексирует selection по UUID владельца, не сохраняет Bukkit references и не вводит persistent storage. `clear(ownerId)` через `remove` позволяет завершать одну сессию, а `clear()` предназначен только для полного завершения владельца store.

Это независимый инфраструктурный срез. Идемпотентность повторного выбора, policy evaluation и события пока остаются ответственностью будущего application service.

## 14. HUD-C.1.8 — Application selection service implemented

Application service оркестрирует выбор, снятие и очистку цели. Все решения допуска делегируются policy port; сам сервис не пытается разрешать Bukkit entities или интеграции. При отклонении или недоступности policy текущая цель сохраняется. Повторный выбор проходит повторную policy-проверку, после чего возвращает AlreadySelected без изменения timestamp.

## 15. Decision

HUD-C.1.5–.8 реализуют immutable domain contracts, transient in-memory store и Bukkit-independent application orchestration. Следующий срез — конкретная policy composition и её изолированные тесты; Paper listener будет отдельным этапом после этого. No command syntax, cycling UX, global glowing outline, persistence or client-mod requirement is approved by this document.
