# AlphaBuilder

Paper 1.21.11 • Java 21 • Maven

## Возможности текущей версии
- `/ab` — главное GUI.
- Отдельный мир `alphabuilder` создаётся автоматически.
- Шаблоны: дом, башня и фонтан.
- Предпросмотр построек и подтверждение через `/ab confirm`.
- `/ab ai <вопрос>` — AI-помощник через совместимый Chat Completions API.
- PvP, Spleef, паркур и турниры пока представлены меню-заготовками, а не полноценными игровыми режимами.

## Сборка
GitHub Actions собирает JAR на Java 21. Открой вкладку Actions, дождись успешного `Build AlphaBuilder`, затем скачай artifact `AlphaBuilder-JAR`.

Локально: `mvn -U clean package`. Результат: `target/AlphaBuilder.jar`.

## AI
Задай переменную окружения `ALPHABUILDER_API_KEY` на сервере. Не публикуй API-ключ в репозитории.
