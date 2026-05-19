# Cringe Volume Player

Клиент-серверный аудиоплеер с намеренно неудобным регулятором громкости.
Каждое изменение громкости требует прохождения оплаты через фейковый платёжный шлюз **CringePay**.

## Архитектура

```
cringe-volume/
├── backend/              Spring Boot REST API + mock-платёжный шлюз + веб-клиент
│   └── src/main/
│       ├── java/com/cringe/volume/
│       │   ├── config/       CorsConfig, MailConfig
│       │   ├── controller/   AudioController (REST + стриминг)
│       │   ├── dto/          UploadResponse, TrackInfo, VolumeRequest, ...
│       │   ├── exception/    GlobalExceptionHandler
│       │   ├── mail/         ReceiptMailService (чеки по email)
│       │   ├── model/        PlayerState
│       │   ├── payment/      PaymentController, PaymentService, PaymentPricingService, ...
│       │   └── service/      AudioService (загрузка, треки, стриминг)
│       └── resources/
│           ├── static/       Веб-клиент (index.html, app.js, style.css)
│           ├── templates/    pay.html (mock-страница оплаты, Thymeleaf)
│           └── application.yml
├── frontend/             JavaFX приложение (.exe через jpackage)
│   └── src/main/java/com/cringe/player/
│       ├── api/          ApiConfig, AudioApiClient
│       ├── payment/      PaymentDialogController
│       ├── player/       AudioPlayerEngine
│       └── ui/           MainController + FXML
├── .env                  Конфигурация (НЕ коммитится)
├── docker-compose.yml
└── README.md
```

## Требования

- **Docker** + **Docker Compose** — для backend
- **Java 17+** + **Maven 3.8+** — для frontend / локальной разработки
- Аудиофайлы **mp3** или **wav**

## Быстрый старт

### 1. Backend (Docker)

```bash
cd cringe-volume
# Скопировать .env из шаблона (если первый запуск)
# cp .env.example .env
docker compose up --build
```

Backend: `http://localhost:8080`
Веб-клиент: `http://localhost:8080` (static/index.html)

### 2. Frontend (JavaFX)

```bash
cd cringe-volume/frontend
mvn javafx:run
```

Или с указанием URL бэкенда:

```bash
mvn javafx:run -Dbackend.url=https://pay.vernovpn.com
```

## Конфигурация (.env)

| Переменная               | По умолчанию                   | Описание                                 |
|--------------------------|--------------------------------|------------------------------------------|
| `PAYMENT_MODE`           | `mock`                         | Режим оплаты (только mock)               |
| `ROLLYPAY_SIGNING_SECRET`| `cringe-super-secret-key-2024` | Секрет для HMAC-SHA256 подписи вебхуков  |
| `SERVER_PORT`            | `8080`                         | Порт бэкенда                             |
| `PUBLIC_BACKEND_URL`     | `http://localhost:8080`        | Публичный URL бэкенда (за прокси)        |
| `PUBLIC_PAY_BASE_URL`    | `http://localhost:8080`        | URL страницы оплаты (за прокси)          |
| `PAYMENT_CALLBACK_URL`   | `.../api/payments/webhook`     | Публичный URL вебхука (документация)     |
| `PAYMENT_CALLBACK_INTERNAL_URL` | `http://localhost:8080/...` | **Внутренний** URL для self-вебхука (Docker→сам себя) |
| `MUSIC_DIR`              | `./music`                      | Директория с аудиофайлами                |
| `CORS_EXTRA_ORIGINS`     | —                              | Доп. CORS-домены через запятую           |
| `SMTP_HOST`              | `localhost`                    | SMTP-сервер для чеков                    |
| `SMTP_PORT`              | `465`                          | Порт SMTP (465 = SSL/implicit TLS)       |
| `SMTP_USER`              | —                              | SMTP логин                               |
| `SMTP_PASSWORD`          | —                              | SMTP пароль                              |
| `SMTP_FROM_EMAIL`        | —                              | Email отправителя чеков                  |
| `SMTP_FROM_NAME`         | `Cringe Volume Player`         | Имя отправителя                          |
| `SMTP_USE_TLS`           | `true`                         | SSL для SMTP (true для порта 465)        |
| `PUBLIC_WEB_URL`         | `http://localhost:8080`        | URL веб-клиента (CORS)                  |

## API

### Аудио

| Метод | URL                                    | Описание                              |
|-------|----------------------------------------|---------------------------------------|
| POST  | `/api/audio/upload`                    | Загрузка аудиофайла (multipart)       |
| POST  | `/api/audio/play`                      | Начать воспроизведение                |
| POST  | `/api/audio/stop`                      | Остановить воспроизведение            |
| POST  | `/api/audio/volume`                    | Установить громкость `{volume: 0-100}`|
| GET   | `/api/audio/state`                     | Состояние плеера                      |
| GET   | `/api/audio/tracks`                    | Список треков на сервере              |
| GET   | `/api/audio/tracks/{filename}/stream`  | Стриминг трека (поддержка Range)      |

### Платежи

| Метод | URL                              | Описание                            |
|-------|----------------------------------|-------------------------------------|
| POST  | `/api/payments/create`           | Создать платёж `{targetVolume}`      |
| GET   | `/api/payments/{token}/status`   | Статус платежа                      |
| POST  | `/api/payments/{token}/process`  | Обработать платёж `{email?}`        |
| POST  | `/api/payments/webhook`          | Webhook (HMAC-SHA256 верификация)   |
| GET   | `/pay/{token}`                   | Mock-страница оплаты (Thymeleaf)    |

## Флоу оплаты громкости

1. Пользователь вводит громкость (0-100) и нажимает «Применить»
2. Клиент (JavaFX / веб) вызывает `POST /api/payments/create` → получает `token`, `payUrl`
3. Открывается страница оплаты CringePay в браузере
4. На странице:
   - Сумма, описание, обратный отсчёт (5 мин)
   - QR-код СБП (декоративный, генерируется из токена)
   - Поля карты (номер / срок / CVV / имя) — **без валидации**
   - Email для чека (необязательно)
   - Промокод — **всегда** «Промокод недействителен»
5. «Оплатить» → спиннер ~2-3 сек → «Платёж успешен»
6. Backend отправляет **себе** подписанный webhook (`HMAC-SHA256`)
7. Webhook проверяет `X-Signature`, помечает платёж `paid`, применяет громкость
8. Если указан email — отправляет чек на почту (SMTP, не блокирует платёж)
9. Клиент (polling) видит `paid` → обновляет UI

## Email-чеки

При оплате можно указать email. После успешного платежа бэкенд отправляет HTML-чек через SMTP.

Настройки SMTP:
- Порт **465** = implicit TLS/SSL (`SMTP_USE_TLS=true`)
- Порт **587** = STARTTLS (установить `SMTP_USE_TLS=false`, добавить `mail.smtp.starttls.enable=true`)
- Сбой отправки чека **не влияет** на статус платежа (асинхронная отправка)

## Кринж-ценообразование

- Базовая цена: случайная 19-99 ₽
- Громкость > 50 → цена **x3** (тариф «громкий режим»)
- Комиссия сервиса: +15 ₽
- Промокод: всегда недействителен

Никакие реальные платежи не проводятся. `PAYMENT_MODE=mock`.

## Деплой на VPS (два поддомена)

### DNS

```
A  music.vernovpn.com  → <IP сервера>
A  pay.vernovpn.com    → <IP сервера>
```

### Caddyfile

```caddyfile
# Веб-клиент (музыкальный плеер)
music.vernovpn.com {
    reverse_proxy localhost:8080
}

# Страница оплаты + API
pay.vernovpn.com {
    reverse_proxy localhost:8080
}
```

Caddy автоматически получит TLS-сертификаты через Let's Encrypt.

### .env для продакшена

```env
SERVER_PORT=8080
PUBLIC_BACKEND_URL=https://pay.vernovpn.com
PUBLIC_PAY_BASE_URL=https://pay.vernovpn.com
PUBLIC_WEB_URL=https://music.vernovpn.com
PAYMENT_CALLBACK_URL=https://pay.vernovpn.com/api/payments/webhook
```

### Запуск

```bash
docker compose up -d --build
caddy run --config /etc/caddy/Caddyfile
```

## Музыкальная директория

Бэкенд сканирует `MUSIC_DIR` (по умолчанию `./music`) при запросе списка треков.
Поддерживаемые форматы: `.mp3`, `.wav`.

Положите файлы в директорию — они сразу появятся в списке.
В Docker директория монтируется как named volume `music`.

## Сборка JavaFX .exe (jpackage)

### Требования
- JDK 17+ с `jpackage` в PATH
- Maven 3.8+ (или встроенный в IntelliJ — `<IDEA>/plugins/maven/lib/maven3/bin/mvn.cmd`)
- JavaFX **jmods** — [gluonhq.com/products/javafx](https://gluonhq.com/products/javafx/),
  вариант **jmods** для нужной OS
- WiX Toolset 3.x — **только** если нужен `.exe`/`.msi` установщик; для app-image не нужен
- Иконка `clown.ico` (256×256 ICO) в `frontend/src/main/resources/icons/` — **опционально**

### Быстрая сборка (Windows)

Открой `cringe-volume/frontend/build-exe.bat`, поправь:
```bat
SET JAVAFX_JMODS=C:\путь\до\javafx-jmods-17.0.x
SET BACKEND_URL=https://pay.vernovpn.com
SET MVN="C:\путь\до\mvn.cmd"
```

Запусти в PowerShell:
```powershell
cd cringe-volume\frontend
.\build-exe.bat
```

Результат: `frontend/target/installer/Cringe Volume Player/Cringe Volume Player.exe` —
**самодостаточная папка** с встроенной JRE. Для распространения копируй всю папку.

### Ручная сборка (кроссплатформенно)

```bash
cd cringe-volume/frontend

# 1. Fat-jar (все зависимости КРОМЕ JavaFX — она придёт через jmods)
mvn clean package -DskipTests

# 2. Подготовить отдельную папку с одним jar
mkdir -p target/app
cp target/volume-frontend-1.0.0-fat.jar target/app/cringe-player.jar

# 3. jpackage
jpackage \
  --type app-image \
  --name "Cringe Volume Player" \
  --app-version 1.0.0 \
  --vendor "CringeWare" \
  --input target/app \
  --main-jar cringe-player.jar \
  --main-class com.cringe.player.Launcher \
  --module-path /path/to/javafx-jmods-17.0.11 \
  --add-modules javafx.controls,javafx.fxml,javafx.media \
  --java-options "-Dbackend.url=https://pay.vernovpn.com" \
  --icon src/main/resources/icons/clown.ico \
  --dest target/installer
```

### Типы вывода

| `--type`     | Что получится                       | Требует WiX |
|--------------|-------------------------------------|-------------|
| `app-image`  | Папка с .exe внутри (рекомендуется) | Нет         |
| `exe`        | Один .exe-установщик                | Да          |
| `msi`        | .msi-установщик                     | Да          |

### Зачем `Launcher`
`CringePlayerApp` наследует `Application`, и jpackage **не может** использовать
такой класс как `--main-class` напрямую — JavaFX runtime требует обёртку.
`com.cringe.player.Launcher` — простой класс, вызывающий `CringePlayerApp.main()`.

### Почему fat-jar исключает JavaFX
`maven-shade-plugin` собирает все зависимости в один jar, **кроме** `org.openjfx:*`.
JavaFX поставляется в jpackage через `--module-path jmods`. Если бы JavaFX-классы
были и в fat-jar (classpath), и в jmods (module path), JVM бросил бы
split-package error.

## Безопасность

- **HMAC-SHA256** верификация вебхуков (`X-Signature = hmac(secret, timestamp + "." + body)`)
- **Path traversal** защита при стриминге и загрузке файлов
- **CORS** ограничен настроенными доменами
- `PAYMENT_MODE=mock` — реальные платежи невозможны
- `.env` не коммитится (в `.gitignore`)
