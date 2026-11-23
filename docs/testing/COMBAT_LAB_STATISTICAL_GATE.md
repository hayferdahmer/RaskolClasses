# Combat Lab Statistical Gate — RaskolClasses 1.16

## Назначение

Статистический gate сравнивает результаты воспроизводимых PvP/PvE-сценариев с утверждённым baseline. Он дополняет, но не заменяет Java unit tests, Combat CI Guard, Spotless, SpotBugs и Paper Runtime Smoke.

Текущий этап добавляет формат отчёта, валидатор, baseline-контракт и регрессионные тесты анализатора. **Полноценный игровой gate пока не включён:** существующий `BalanceSimulator` — упрощённая модель дуэли, а не источник достоверных значений по всем требуемым метрикам. До подключения headless runner к production combat math baseline намеренно имеет статус `pending`.

## Контракт отчёта

JSON Schema: `tools/combat-lab/schemas/combat-lab-report.schema.json`.

Обязательные метаданные:
- версия схемы и симулятора;
- идентификатор набора сценариев;
- SHA исходного commit;
- начальный seed и число итераций;
- время генерации;
- полный набор сценариев, ожидаемый baseline.

Обязательные метрики каждого сценария:
- win rate с 95% доверительным интервалом;
- медиана TTK;
- средний DPS и HPS;
- фактическая частота proc;
- доля урона питомца;
- доля потерянного ресурса;
- список нарушенных игровых инвариантов.

Все долевые метрики нормированы в диапазоне 0..1. Для каждой метрики отчёт обязан содержать CI95 и метод расчёта:
- `win_rate`, `proc_rate`: Wilson interval;
- `pet_damage_share`, `resource_waste_pct`, TTK, DPS и HPS: bootstrap percentile interval.

CI95 должен содержать точечную оценку и целиком помещаться в допустимый диапазон отклонения от baseline. Если интервал пересекает границу допуска, результат не считается доказанно стабильным и gate завершается ошибкой. Для каждого сценария требуется не менее 1200 прогонов: меньшая выборка не обеспечивает достаточную точность для заданных допусков win rate и proc rate. Не допускаются NaN, Infinity, отрицательные значения, неполная матрица сценариев или несовместимые версии симулятора.

## Начальные пороги сравнения

Пороги — стартовые guardrails для обнаружения крупных регрессий, а не утверждённый live-баланс:

| Метрика | Допустимое отклонение от baseline |
| --- | ---: |
| Win rate | 5 процентных пунктов |
| Proc rate | 3 процентных пункта |
| Pet damage share | 5 процентных пунктов |
| Resource waste | 5 процентных пунктов |
| Median TTK | 10% |
| Mean DPS | 10% |
| Mean HPS | 10% |

Любое нарушение инварианта — FAIL независимо от среднего значения. В дальнейшем пороги должны уточняться по дисперсии и мощности выборки, а не расширяться ради зелёного CI.

## Правила baseline

1. Baseline нельзя генерировать из синтетических fixtures или вручную придумывать.
2. Утверждённый baseline содержит точный набор scenario ID, версию симулятора и source commit.
3. Изменение формулы или рангового коэффициента требует отдельного review baseline diff.
4. Нельзя обновлять baseline автоматически при падении gate.
5. Смена версии симулятора требует новой калибровки; отчёты разных версий напрямую не сравниваются.
6. Для каждого scenario CI должен проверять все предусмотренные итерации и seeds. Уменьшение выборки не должно скрывать регрессию.

## Первый headless probe

`CombatLabRunner` запускает отдельный калибровочный профиль `pvp-unarmed-melee-probe-v1`:
- 6 классов × 5 контрольных уровней × 30 направленных матчапов = 150 сценариев;
- не менее 1200 воспроизводимых дуэлей на сценарий;
- seed фиксируется, bootstrap CI использует детерминированный seed;
- снимки атрибутов используют `AttributeMath` и `PowerService` production pure formulas;
- резисты используют `CombatMath.mitigation`, который также вызывается production `ResistService`;
- анти-ваншот использует `CombatMath.cappedDamage`, общий с production `CombatService`;
- CI проверяет полноту матрицы, уникальность scenario ID, инварианты и корректность CI.

Это намеренно **не полный боевой симулятор**. В probe зафиксированы безоружный физический удар и интервал атаки 0.8 с. Не моделируются экипировка/weapon mastery, активные способности, таланты, проки, питомцы, CC, школы, burst-window и экономика ресурсов. Поэтому probe-отчёт имеет собственный `combat-lab-probe-report` формат и отдельную JSON Schema; его нельзя передавать в полный baseline comparator и нельзя использовать для утверждения баланса.

Запуск из корня репозитория после компиляции:

```bash
mvn -Pdev -B -DskipTests verify
mvn -Pdev -B org.codehaus.mojo:exec-maven-plugin:3.5.0:java \
  -Dexec.mainClass=dev.raskol.classes.combat.lab.CombatLabRunner \
  -Dexec.classpathScope=compile \
  -Dexec.args="--config src/main/resources/config.yml --output target/combat-lab/unarmed-probe-report.json --seed 20261123 --iterations 1200 --source-commit $(git rev-parse HEAD)"
python3 tools/combat-lab/validate_probe_report.py target/combat-lab/unarmed-probe-report.json
```

## Статус интеграции

- [x] Формат отчёта и JSON Schema.
- [x] Версионированный baseline placeholder со статусом pending.
- [x] Comparator с проверкой полного набора сценариев, версии, выборки, CI и инвариантов.
- [x] Регрессионные тесты анализатора.
- [x] Первый ограниченный headless probe: снимки классов из `config.yml`, shared production pure math, deterministic duel engine и отдельный probe-report.
- [ ] Полный headless runner для экипировки, способностей, талантов, проков, питомцев, CC, школ и ресурсной экономики.
- [ ] Утверждённый baseline по PvP/PvE матрицам.
- [ ] Обязательное сравнение в Combat CI Guard для combat-related PR.
- [ ] Расширенный nightly/manual профиль с тысячами прогонов.
- [ ] Paper 26.2 runtime smoke отдельно от headless результата.

Команда сравнения после утверждения baseline:

```bash
python3 tools/combat-lab/compare_reports.py \
  --baseline tools/combat-lab/baselines/v1.16.0.json \
  --report target/combat-lab/report.json
```

Проверка структуры текущего baseline:

```bash
python3 tools/combat-lab/compare_reports.py \
  --baseline tools/combat-lab/baselines/v1.16.0.json \
  --validate-only
```

## Безопасность интерпретации

Headless PASS означает только отсутствие регрессий в покрытых сценариях симулятора. Он не доказывает live PvP-баланс и не заменяет тесты событий Paper 26.2. Runtime Smoke публикуется отдельным статусом; отсутствие runtime окружения не превращается в PASS.
