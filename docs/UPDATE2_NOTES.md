# update2: Telegram login, deposit memo + receipt, monthly settlement

## Что изменилось
- **Вход только через Telegram Mini App.** Паролей нет. Сервер проверяет подпись `initData` (HMAC по токену бота),
  свежесть `auth_date` (по умолчанию 1 час), аккаунт привязан к `telegramId`, username берётся из Telegram
  (санитизируется под политику a-z0-9_, 3-24). Эндпоинты: `POST /api/auth/telegram`, `/logout`, `/delete`, `GET /api/auth/me`.
  `POST /api/auth/dev-login` существует только при `slots.auth.dev-login-enabled=true` (профиль test), иначе 404.
- **Депозит: memo + квитанция вместо скриншота.** Каждый депозит получает memo `BDxxxxxxxx` (его игрок ставит в комментарий перевода).
  При "Sent" игрок вставляет hash транзакции или ссылку на explorer, сервер нормализует до 64-hex, hash уникален.
  Админ видит memo и ссылку на tonviewer; при Approve используется сохранённая квитанция. При Reject квитанция освобождается.
- **Месячное закрытие.** `[ Settle house profit ]` записывает вывод house profit (таблица `owner_draws`), призовой пул не трогается.
  `[ Reconciliation ]` показывает сверку и `expectedWalletBalance` (= депозиты - выплаты - выводы владельца + seed): сколько должно быть на кошельке.
  Кнопка Reset bankroll убрана из UI (эндпоинт остаётся выключенным).

## Деплой
1. В `.env` на сервере: `TELEGRAM_BOT_TOKEN=...` (+ три ADMIN_* как раньше, хеш в одинарных кавычках).
2. В `docker-compose.yml` в `environment:` приложения добавить `TELEGRAM_BOT_TOKEN: ${TELEGRAM_BOT_TOKEN}` и (если нужен Telegram Web) `COOKIE_SAME_SITE: ${COOKIE_SAME_SITE:-Strict}`.
3. BotFather: Mini App / Menu Button URL = твой https-адрес (тот же домен, что отдаёт приложение).
4. БД дропнуть (схема изменилась: `users.telegram_id`, `balance_transactions.deposit_memo`, `owner_draws`; колонка пароля удалена).
5. Telegram Web (браузерная версия) открывает приложение в iframe: нужен `COOKIE_SAME_SITE=None` (только при `COOKIE_SECURE=true`).
6. Прокси должен передавать оригинальный `Host` (проверка Origin).

## Что проверять руками
- Админ при Approve сверяет в explorer: сумма, комментарий == memo, hash == квитанция игрока.
- seed (`BANKROLL_INITIAL_PRIZE_POOL`) считается внесённым на кошелёк.
- Игрок, открывший приложение через час после входа, остаётся в системе по cookie-сессии; для нового входа нужен свежий `initData` (переоткрыть из бота).
