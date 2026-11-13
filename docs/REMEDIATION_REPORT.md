# BADDROP — remediation report

## Read this first: what is and is not verified

| | Status |
|---|---|
| Pure helpers (`UsernamePolicy`, `PasswordPolicy`, `TonAddressValidator`, `Totp`, `RateLimiter`, `Money`) | **Compiled with javac and executed** here: 46 assertions pass, including the RFC 6238 test vectors and the real CRC16 of your TON address |
| `app.js`, `admin.js`, `admin-login.js` | **Syntax-checked** with Node (`node --check`). Not run in a browser |
| All Spring code (entities, repositories, services, controllers, interceptor) | **Written, NOT compiled.** The sandbox proxy blocks `repo.maven.apache.org` (403 policy denial), so Spring Boot 4 / Hibernate / H2 could not be downloaded. I did not try to route around the block |
| All integration / security / concurrency / invariant / bankroll tests | **Written, NOT run.** Same reason. A syntax-only javac pass found no syntax errors (two were found and fixed); type errors against Spring/Jackson 3 can only show up on your machine |

Treat this as a **complete first draft that has never been through a compiler**. Expect a handful of compile errors and a few
test failures on the first `mvn test`; that is normal and the fix list should be short. Run it before trusting any of it,
and send me the output if you want me to iterate.

A developer is working on the same spec in parallel. Both branches rewrite the same files (`UserService`, controllers, `User`,
`app.js`, tests), so **do not merge them blindly**: pick one as the base and cherry-pick from the other.

## Status per finding

| ID | Finding | Status | Where |
|---|---|---|---|
| C1 | Auth optional on money endpoints | **Done** | `AuthInterceptor` (default-deny + public allow-list); acting user always from the session; blank password can never match |
| C2 | Stored XSS | **Done** | `UsernamePolicy` (`[a-z0-9_]{3,24}`); `admin.js` uses `textContent`; `escapeHtml`/`safeImageUrl` in `app.js` templates; strict CSP |
| C3 | Plaintext passwords | **Done** | BCrypt (cost 12); `LegacyPasswordMigrator` hashes old rows then drops `users.password` |
| C4 | Credentials on the client / in URLs | **Done** | HttpOnly+Secure+SameSite=Strict cookie, CSRF header, nothing secret in storage or URLs; old `sessionStorage` entries are purged on load |
| C5 | Race conditions | **Done** | User row lock first on every buy/open/instant-open/withdraw/deposit; `@Version`; fixed lock order user → tx → bankroll; stale-entity bug in approve/reject fixed |
| C6 | Weak admin access | **Partial** | Admin = username + BCrypt + TOTP (replay-protected) + server session + audit; shared key and `ADMIN_SESSION` cookie removed. Still a single configured admin; no roles |
| C7 | `deleteAccount` destroys records | **Done** | Soft delete; refused while balance, inventory or a transaction is open; username stays reserved |
| C8 | Deposits not verified | **Partial** | TON address checksum, admin must enter the on-chain hash, hash unique (no double credit). **No automatic on-chain verification** — see decisions |
| H9 | Session lifecycle | **Done** | Opaque tokens (SHA-256 stored), expiry, revoke on logout and on password change, purge job. No idle timeout |
| H10 | Rate limiting | **Done** | Login per IP and per user+IP, register, admin login, password re-check. In-memory: one node only |
| H11 | Public user list | **Done** | `/api/users*` removed; masked leaderboard only |
| H12 | Effective RTP ≠ advertised | **Not done** | Needs an engine-level decision and a measurement run; see decisions |
| H13 | `validateMathematics` ignores RTP | **Partial** | RTP ≤ 93% check added; warns by default, refuses to start with `slots.catalog.enforce-rtp-cap=true` (I could not see `initial-cases.json`, so I did not make it fatal) |
| H14 | No ledger / spin history | **Partial** | Append-only audit log for admin actions and failed logins. **No per-player ledger or spin log** |
| H15 | No reconciliation; destructive reset | **Partial** | `GET /api/admin/reconciliation` (includes inventory value and pending withdrawals); reset disabled by default and audited. Does not compare with the real wallet balance |
| H16 | Input validation | **Done** | `Money` (≤2 decimals, bounded, no `1e3`), TON validator, username/password policy, idempotency-key format |
| H17 | Provably fair | **Not done** | Product decision; see below |
| H18 | Seeder rewrites live paytables | **Not done** | `CaseInitialCatalogConfig` still overwrites `paytableJson` of existing cases at startup |
| H19 | No idempotency | **Done** | `Idempotency-Key` required on deposit start, withdraw, buy, instant-open; key is released if the operation fails |
| H20 | Headers / CORS / cookie flags | **Done, with an assumption** | CSP, nosniff, frame-deny, no-store, Origin check, cookie flags, no CORS. I never saw `index.html`/`admin.html`: if they use inline `<script>`/inline handlers the strict CSP will block them |
| H21 | Username/password policy, enumeration | **Done** | Policies; identical login errors; dummy BCrypt for unknown users. Registration still says "username taken" (unavoidable) |
| H22 | Recovery / withdrawal safety net | **Partial** | Password change kills all other sessions. **No recovery flow, no withdrawal cool-down or address whitelist after a credential change** |

## Decisions that are yours

1. **On-chain deposit verification (C8).** Today a human compares the chain to the screen. Automating it needs a TON indexer
   (TonAPI / toncenter / your own node) and a decision on required confirmations. Until then the admin is the control, and
   the unique `externalTxHash` is the safety net.
2. **RTP policy (H12/H13).** With a 7% house cut, a case with RTP above 93% bleeds the pool. Check the log on first start for
   `exceeds the 93% cap` warnings, then decide to change the paytables or the split, and flip the enforce flag.
3. **Provably fair (H17).** Commit-reveal needs a product/UX decision; not started.
4. **Account recovery (H22).** Needs a recovery channel (email/Telegram). Without one, a forgotten password means a lost balance.
5. **Withdrawal cool-down.** Suggest a delay or admin re-check for withdrawals within N hours of a password change.

## Things that behave differently now (check your UI and ops)

- **API surface changed.** Everything per-player is under `/api/me/*`, auth under `/api/auth/*`, admin login at
  `/api/admin/auth/login`. Old `/api/users/*`, `/api/admin/login`, `/api/users/promo` are gone.
- The promo box on the profile tab no longer talks to the server (it used to double as admin login). Admins go to **`/admin-login.html`**.
- Account deletion asks for the password with `window.prompt` (plain text on screen); admin deposit approval asks for the
  on-chain hash the same way. Both are quick and ugly — replace with proper modals when you touch `index.html`/`admin.html`.
- Usernames are lower-cased and restricted. **Run the two SELECTs in `db/migration-notes.sql` before deploying**: legacy
  usernames with capitals, spaces or symbols can no longer log in.
- `users.version` must be `0`, not `NULL`, for existing rows (SQL provided) or Hibernate misbehaves.
- Behind a proxy set `server.forward-headers-strategy=native`, otherwise every user shares the proxy's IP in the rate limiter.
- `GET /api/admin/audit` returns the entity directly; fine for now, give it a DTO if the entity grows.
- Any currently logged-in player is logged out by this release (sessions live in a new table).

## Tests: what replaced the two old files

`src/test/java/com/dworf/slots_demo/`, selectable by JUnit tag:

| Package / tag | Files | What it proves |
|---|---|---|
| `unit` / `unit` | 7 classes | Policies, TON checksum, TOTP (RFC vectors), rate limiter, money parsing, engine maths on a synthetic paytable. No Spring, no DB |
| `integration` / `integration` | Auth, Wallet, Marketplace, Admin flows | Happy paths and business rules end to end over real HTTP |
| `security` / `security` | Access matrix, CSRF/Origin, cookies, admin auth, TOTP replay, rate limits, IDOR, input hardening, headers | Every non-public endpoint is 401 anonymously; every old backdoor is dead; hostile input yields clean 4xx |
| `concurrency` / `concurrency` | Concurrent money | 10-thread races on withdraw, confirm, approve/reject, buy, open, register, password change |
| `invariant` / `invariant` | Reconciliation | The books do not drift across deposits, withdrawals, buys, opens, and a concurrent mixed load |
| `bankroll` / `bankroll` | Engine + reset | Old `BankrollEngineTests` ported (turn-zero safety, pre-roll filter, exact 7%/93% split, 8-thread spin) + guarded reset |

Contexts with their own settings (rate limits, TOTP replay, reset switch) are separate test classes so the shared
context keeps generous limits. The reset test discards its context afterwards.

## First-run checklist

1. Apply `docs/POM_CHANGES.md`; delete the two old test files.
2. Back up the DB; run `db/migration-notes.sql` (the `(!)` statements at minimum).
3. Set the admin env vars from `docs/application.properties.example` (generate the BCrypt hash and TOTP secret).
4. `mvn test` → fix compile errors → `mvn test -Dgroups=unit` first, then the rest.
5. Start the app, open `/`, register, deposit flow; open `/admin-login.html`; watch the browser console for **CSP violations**.
6. Look at the startup log for RTP warnings and the legacy-password migration line.
