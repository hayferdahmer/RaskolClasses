<!--
© 2026 hayferdahmer. All rights reserved. RASKOL Proprietary License v2.0, see LICENSE.
SPDX-License-Identifier: LicenseRef-Raskol-Proprietary-2.0
-->

# RIPS Operations — Raskol Integrity Protection System

## Назначение

RIPS проверяет происхождение и целостность официального JAR RaskolClasses. Это дополнительный защитный слой плагина; он не заменяет резервное копирование, контроль доступа к серверу, обновление Paper/Java или аудит зависимостей.

## Что находится в официальном JAR

Официальный packager формирует следующие ресурсы:

- `META-INF/raskol/build.properties` — идентификатор продукта, версия, build ID, commit SHA, тип сборки и timestamp.
- `META-INF/raskol/integrity/manifest.rips` — канонический manifest со списком файлов, их размерами и SHA-256.
- `META-INF/raskol/integrity/manifest.sig` — detached Ed25519 signature для manifest.
- `META-INF/raskol/integrity/public-key.txt` — публичный ключ, использованный packager.

**Модель доверия:** наличие `public-key.txt` внутри архива не делает этот ключ доверенным. Runtime verifier использует trusted public key, закреплённый в `TrustedPublicKeyRegistry` исходного кода. Приватный ключ подписи не входит в JAR и не должен попадать в репозиторий, логи, тикеты или локальные скрипты.

## Путь проверки

1. При включении плагин загружает build identity.
2. Для OFFICIAL-сборки verifier получает закреплённые доверенные публичные ключи.
3. Проверяется detached Ed25519 signature до разбора manifest.
4. Сверяются build identity и содержимое файлов, перечисленных в подписанном manifest.
5. Проверяется целостность полного JAR-архива.
6. После успешного старта Runtime Integrity Monitor повторяет проверку каждые 30 минут.
7. Подтверждённое нарушение целостности передаётся enforcement policy и приводит к отключению RaskolClasses. Временные ошибки проверки учитываются отдельным порогом последовательных отказов.

Успешная стартовая проверка отображается в журнале как:

```text
[RIPS] INFO | VERIFIED | Artifact signature, build identity, protected files and complete JAR archive were verified successfully.
```

Фактический счётчик `checkedFiles` зависит от состава официального JAR.

## Проверенная конфигурация и установка

На боевом сервере оператора проверена конфигурация **Paper 26.2 + Java 25**: RaskolClasses 1.15.8 запустился, стартовая проверка RIPS завершилась `VERIFIED` (`checkedFiles=392`). Это подтверждение запуска конкретной сборки и целостности артефакта на указанном окружении, а не полный функциональный тест всех Paper API 26.2.

Исходники текущей ветки компилируются с Java release 21 и Paper API `1.21.4-R0.1-SNAPSHOT`; `plugin.yml` указывает `api-version: '1.21'`. Java 25 запускает байткод Java 21, однако compile-time API 1.21.4 не даёт автоматической гарантии полной совместимости всех API-вызовов с Paper 26.2.

- Production: скачивайте только официальный JAR со страницы [GitHub Releases](https://github.com/hayferdahmer/RaskolClasses/releases).
- Не используйте обычный Maven JAR как официальный артефакт: при отсутствии `build.properties` он определяется как DEVELOPMENT.
- DEVELOPMENT-сборку разрешается запускать только в изолированной тестовой среде с JVM-свойством `-Draskol.rips.development=true`.
- Не включайте это свойство в production startup script и не используйте его для обхода отказа RIPS.

## Если проверка не прошла

1. Не удаляйте и не редактируйте RIPS metadata, trusted-key registry или manifest, чтобы заставить JAR загрузиться.
2. Остановите сервер штатно и сохраните полный лог запуска.
3. Проверьте, что установлен именно официальный JAR нужной версии и что он скачан из GitHub Releases.
4. Если для релиза опубликован SHA-256, сравните его с локальным файлом.
5. Если проблема сохраняется, отправьте приватный отчёт через GitHub Security Advisories. Приложите версию RaskolClasses, версию Paper, версию Java, SHA-256 JAR и релевантные строки лога.
6. Никогда не отправляйте приватный signing key, GitHub tokens, пароли или другие секреты.

### Значимые статусы

| Статус | Значение |
| --- | --- |
| `VERIFIED` | Подпись, build identity, защищённые файлы и архив прошли проверку. |
| `DEVELOPMENT_MODE_REQUIRED` | DEVELOPMENT-сборка обнаружена без явного разрешения JVM. |
| `TRUSTED_KEY_UNAVAILABLE` | Реестр доверенных ключей недоступен или некорректен. |
| `MANIFEST_MISSING` / `SIGNATURE_MISSING` | Отсутствует обязательный ресурс официального артефакта. |
| `INVALID_SIGNATURE` | Detached signature имеет неверный формат или не подтверждается доверенным ключом. |
| `INVALID_MANIFEST` | Manifest не соответствует каноническому формату. |
| `BUILD_IDENTITY_MISMATCH` | Данные сборки не совпадают с подписанной идентичностью. |
| `PROTECTED_FILE_HASH_MISMATCH` | Хеш защищённого файла не совпал с подписанным значением. |
| `ARCHIVE_INTEGRITY_FAILURE` | Проверка полного JAR-архива завершилась ошибкой. |

## CI и релизный процесс

- Обычный build job выполняет license-header gate, Python-тесты release signing policy и `mvn verify`.
- RIPS Security Auditor — read-only workflow: проверяет исходные маркеры архитектуры и запускает профильные Java-тесты. Его успешный результат не является гарантией отсутствия всех уязвимостей.
- Ручной `signing-verification` для `release/1.15.8` создаёт временный OFFICIAL JAR для серверной проверки, но не создаёт тег и не публикует GitHub Release.
- Публикация выполняется tag-triggered workflow для тега `vX.Y.Z`. Перед публикацией проверяется release signing policy и соответствие CI signing key активному публичному ключу.
- Секрет `RIPS_SIGNING_PRIVATE_KEY_B64` хранится только в GitHub Environment `rips-release`. Не переносите его в файлы репозитория и не выводите значение в лог.

## Ограничения

RIPS подтверждает целостность и происхождение подписанного артефакта в рамках реализованных проверок. Он не доказывает безопасность всей логики плагина, не защищает сервер от компрометации ОС/учётной записи администратора и не заменяет независимый аудит. RIPS Security Auditor v1 — детерминированная read-only проверка известных архитектурных инвариантов плюс тесты, а не полноценный SAST/DAST или vulnerability scanner.