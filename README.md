# Metrics Service

REST-сервис приёма и агрегации метрик (тестовое задание). Стек: Java 17, Spring Boot 3.3, PostgreSQL, Redis, Liquibase, Docker Compose.

## Требования из ТЗ 

Методы API только три:

| Метод | Описание |
|--------|----------|
| `POST /sign-up` | Регистрация: **`clientId`** в JSON → запись в БД; затем можно вызвать `POST /auth` |
| `POST /auth` | Вход: **`clientId`** (как в ТЗ), ответ: **JWT** |
| `POST /metrics` | Приём метрики (`timestamp` с TZ, `value` > 0, `payload` JSON), JWT, rate limit |
| `GET /metrics` | Статистика `count`, `avg`, `min`, `max` по query `from`, `to` (ISO 8601 с TZ), JWT, rate limit |

В Liquibase есть демо-клиент `demo`; новых клиентов можно добавлять через **`POST /sign-up`**.

## Архитектура

- **Слой контроллеров**: `AuthController` (`/auth`, `/sign-up`), `MetricsController` (`/metrics`), общий `GlobalExceptionHandler`.
- **Сервисы**: `AuthService` (регистрация клиента, поиск в БД и выдача JWT), `JwtService` (подпись/разбор токена), `MetricsService` (запись метрик и агрегация SQL), `RedisRateLimiter`.
- **Хранение**: JDBC + `NamedParameterJdbcTemplate`, таблицы `clients`, `metrics`; миграции Liquibase.
- **Безопасность**: фильтр `JwtAuthenticationFilter`, правила в `SecurityConfig`.
- **Ограничение частоты**: фильтр `RateLimitFilter` после JWT — счётчики в Redis по секундам.

## Даты и часовые пояса

В API используется `OffsetDateTime` (ISO 8601 с offset). В PostgreSQL поля `TIMESTAMPTZ`; сравнение и агрегация выполняются в сохранённом абсолютном времени.

## Rate limiting 

- **`POST /metrics`**: не более **10** запросов/с на клиента (идентификатор из JWT) и не более **1000** запросов/с суммарно по всем клиентам.
- **`GET /metrics`**: не более **10** запросов/с на клиента (глобальный лимит 1000 в ТЗ для GET не указан — не применяется).

Реализация: Redis, ключи по секунде (`epoch`), см. `RedisRateLimiter` и `RateLimitFilter`.

## Выбор БД

PostgreSQL: реляционная модель, `JSONB` для `payload`, типы `NUMERIC`/`TIMESTAMPTZ`, индексы по времени и `(client_id, ts)` для записи метрик от клиентов.

## Масштабирование

- Горизонтально: несколько инстансов приложения за балансировщиком; общие PostgreSQL и Redis (rate limit распределённый).
- Узкие места: БД при большом объёме метрик — партиционирование `metrics` по времени, реплики для чтения агрегатов.

## OpenAPI / Swagger (вне объёма ТЗ, для удобства)

- Спецификация: `GET http://localhost:8080/v3/api-docs`
- UI: `http://localhost:8080/swagger-ui.html`

## Ошибки при доступе к `/metrics` (JWT)

Ответы в JSON в формате `ApiError` (`code`, `message`, `details`). Типичные случаи:

| HTTP | `code` | Когда |
|------|--------|--------|
| 401 | `AUTHENTICATION_REQUIRED` | Нет заголовка `Authorization` или нет схемы Bearer |
| 401 | `EXPECTED_BEARER` | Заголовок есть, но не `Bearer …` (например `Basic`) |
| 401 | `INVALID_TOKEN` | Пустой токен, битая подпись, неверный формат JWT |
| 401 | `TOKEN_EXPIRED` | Истёк срок `exp` |
| 429 | `RATE_LIMIT_EXCEEDED` | Превышен rate limit (ТЗ) |

## Схема БД

`clients`: `id`, `client_id`, `secret_hash` (не используется при входе по ТЗ), `enabled`, аудит.

`metrics`: `id`, `client_id`, `ts`, `value` (> 0), `payload` (jsonb), аудит.

## API (примеры)

### `POST /sign-up`

```json
{ "clientId": "my-client" }
```

Ответ `201`:

```json
{ "clientId": "my-client", "status": "created" }
```

При дублирующем `clientId` — **409** (`clientId already exists`).

### `POST /auth`

```json
{ "clientId": "demo" }
```

Ответ:

```json
{ "jwt": "<JWT>" }
```

### `POST /metrics`

Заголовок: `Authorization: Bearer <jwt>`

```json
{
  "timestamp": "2026-04-28T15:00:00+03:00",
  "value": 12.34,
  "payload": { "source": "ui" }
}
```

### `GET /metrics`

Заголовок: `Authorization: Bearer <jwt>`

Параметры: `from`, `to` — ISO 8601 с часовым поясом.

## Команды для сборки и запуска локально

### Docker Compose (рекомендуется)
Перейти в корневую директорию проекта
```bash
docker compose -p metrics-service up -d --build
```

UI: `http://localhost:3000` (прокси на API).

### Maven (без Docker)

Нужны PostgreSQL и Redis; переменные окружения как в `application.yml` / `docker-compose.yml`.

```bash
mvn -DskipTests clean package
java -jar target/metrics-service-0.0.1-SNAPSHOT.jar
```

### Примеры `curl`

```bash
curl -s -X POST http://localhost:8080/sign-up \
  -H "Content-Type: application/json" \
  -d '{"clientId":"my-client"}'

curl -s -X POST http://localhost:8080/auth \
  -H "Content-Type: application/json" \
  -d '{"clientId":"demo"}'

TOKEN=$(curl -s -X POST http://localhost:8080/auth \
  -H "Content-Type: application/json" \
  -d '{"clientId":"demo"}' | sed -E 's/.*"jwt":"([^"]+)".*/\1/')

curl -X POST http://localhost:8080/metrics \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"timestamp":"2026-04-29T14:30:00+03:00","value":12.34,"payload":{"source":"curl"}}'

curl -G "http://localhost:8080/metrics" \
  -H "Authorization: Bearer $TOKEN" \
  --data-urlencode "from=2026-04-29T14:00:00+03:00" \
  --data-urlencode "to=2026-04-29T15:00:00+03:00"
```

Остановка: `docker compose -p metrics-service down` (с томами: `down -v`).

Для просмотра логов: `docker compose -p metrics-service logs -f`
