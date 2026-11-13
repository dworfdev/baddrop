# BADDROP — Security & Integrity Remediation Specification

**Scope:** Critical (C1–C8) and High (H9–H22) findings from the pre-refactoring audit.
**Codebase:** Spring Boot (Java, JPA/Hibernate), static JS frontend (`app.js`, `admin.js`).
**Context:** The platform handles real money (USDT on TON) and gambling-style math. Every finding below is treated as a production blocker unless stated otherwise.

---

## 0. Ground Rules

1. **Do not refactor for style until C1–C8 are closed.** Security fixes first; the `UserService` split comes after.
2. **Withdrawals must be disabled (feature flag or config switch) from the moment work starts until C1, C2, C5 and C7 are deployed.** See Section 0.1.
3. Every fix ships with **automated tests** (unit + integration; concurrency tests where stated). A finding is not "done" without its acceptance criteria passing in CI.
4. Every schema change ships as a **versioned migration** (Flyway or Liquibase). `spring.jpa.hibernate.ddl-auto` must be `validate` or `none` in production.
5. Business math (paytables, 7/33/60 split) must not change unless a finding explicitly says so.
6. All new secrets come from environment/secret storage. Nothing secret in the repo, in `application.properties` defaults, or in frontend code.
7. API compatibility with the current frontend is **not** required where a finding demands a breaking change; the frontend is updated in the same release.

### 0.1 Immediate incident review (before/with deployment)

Because C1 allows operations without a password, assume exploitation may already have happened. Before shipping:

- Grep access logs for calls to `/api/users/balance/withdraw`, `/items/buy`, `/items/open`, `/cases/instant-open`, `/balance/deposit/*` where the body had no/empty `password`.
- List all APPROVED/PENDING withdrawals and check wallet addresses reused across multiple usernames, and users whose balance dropped without a matching login pattern.
- Check usernames for HTML/script characters (C2).
- Treat **all existing passwords as compromised** (they were stored in plaintext, sent in URL query strings, and kept in `sessionStorage`). See C3 for forced rotation.
- Reconcile virtual totals against the real wallet (see H15 invariant) to establish a baseline.

---

## CRITICAL

### C1. Authentication is optional on money-moving and data endpoints

**Location:** `UserService.authenticateAndLockUser`, `UserService.authenticateOrFindUser` (and callers: `startDepositSession`, `confirmDepositSent`, `withdraw`, `instantOpenCase`, `buyItem`, `openCase`, `getActiveDepositSession`, `getUser`, `getUserBalance`, `getUserItems`).

**Finding.** Both helpers only verify the password `if (password != null && !password.isBlank())`. If the client omits the password, the check is skipped and the request runs as that user. A comment says this is for "admin operations", but admin operations already have a separate path (`AdminAuthInterceptor`).

**Why this is critical.**
- Anyone who knows (or enumerates via `GET /api/users`, see H11) a username can: withdraw that user's balance to an attacker's TON wallet (creates a normal-looking PENDING request that an admin will approve), buy/open cases with the victim's balance, read their deposit session, balance and inventory.
- Denial of service on withdrawals: starting/confirming a deposit session for a victim puts them in PENDING state, and `withdraw` rejects users with a PENDING transaction.
- The only remaining barrier is a human admin who sees nothing suspicious. This is theft with a single unauthenticated HTTP request.

**Required changes.**
1. Delete the optional-password branches. There must be **no code path** that resolves a user for a player-facing operation without a verified credential.
2. Remove `username`/`password` from every player request DTO. After C4 is done, the acting user comes from the authenticated principal (session), never from the request body or query.
3. Replace the four authentication helpers (`authenticateUser`, `authenticateOrFindUser`, `authenticateAndLockUser`, `findUserByUsername*`) with: `AuthService.login(...)` (credential check, once) and `CurrentUser` resolution from the session. Separate `UserRepository` lookups for admin use must live in an admin-only service behind admin auth.
4. Default-deny: all `/api/**` endpoints require authentication except an explicit allowlist (`/api/users` POST register, `/api/users/login`, `/api/config`, health).
5. Locking: mutating operations load the user with `findByUsernameForUpdate` (or by id) inside the transaction (see C5).

**Acceptance criteria.**
- An automated test enumerates every controller mapping and asserts that unauthenticated requests receive 401 (except the explicit allowlist).
- A request with a valid username and no/empty/wrong password never changes state and never returns user data.
- Static check/grep in CI: no method named `authenticateOrFindUser`; no use of `findByUsername` in player flows.

---

### C2. Stored XSS in the admin panel (and unsafe `innerHTML` in the player UI)

**Location:** `admin.js` → `renderTransactionsList` (`tx.username` injected into `innerHTML`, inside text and inside `data-open-user` / `title` attributes), `renderUserStatsTimeline` (`title` attribute built from data). `app.js` → several `innerHTML` templates (`crate.imageUrl`, `crate.name`, `crate.slug`, `labelText`, `displayLabel`). Backend: usernames are only checked for non-blank (`validateCredentialsInput`).

**Finding.** An attacker registers a username like `"><img src=x onerror=...>`, creates a deposit/withdraw request, and waits for an admin to open the pending list. The script executes in the admin's origin.

**Why this is critical.** In the admin origin the script can: read `sessionStorage.adminKey` (the admin password, stored there by `app.js`/`admin.js`), call `/approve` on attacker-controlled transactions, call `/bankroll/reset`, read all users' data, and exfiltrate the admin password. One crafted username compromises the entire platform and all funds. Case fields (`name`, `imageUrl`, `slug`) are admin-controlled today, but must still be treated as untrusted output; they will be edited by tooling or compromised accounts eventually.

**Required changes.**
1. **Backend input validation (defense layer 1):** usernames must match an allowlist such as `^[a-z0-9_]{3,20}$` after normalization (see H21). Reject everything else with 400.
2. **Frontend output encoding (the real fix):** eliminate `innerHTML` with interpolated data. Build DOM with `createElement` + `textContent` + `setAttribute`/`dataset`, or use a single audited `escapeHtml()` helper where templating is unavoidable. Specifically fix: all of `admin.js` template strings; `app.js` `casesMarketplaceGrid`, `inventoryGrid`, `caseInspectTableBody`, roulette slots. Validate `imageUrl` to be a same-origin path or an allowlisted host.
3. **CSP (defense layer 3):** `Content-Security-Policy` with `default-src 'self'; script-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'`; no `unsafe-inline` for scripts (move any inline `<script>` in `index.html`/`admin.html` to files). See H20.
4. Add ESLint rules (`no-unsanitized`, `no-inline-handlers`) to CI so new `innerHTML` sinks fail the build.
5. Migrate/clean existing usernames that violate the new policy (rename with a forced notice, or block login until they pick a new one).

**Acceptance criteria.**
- Test: registering `<script>`/attribute-breaking payloads is rejected (400). Pre-existing data containing such characters renders as inert text in the admin UI (verified with a Playwright/Cypress test).
- CSP header present on all HTML responses; browser console shows no violations on normal flows.
- `grep -R "innerHTML" src/main/resources/static` shows only constant strings or none.

---

### C3. Passwords stored in plaintext

**Location:** `User.password`, `UserService.createUser`, `changePassword`, `authenticateUser` (`user.getPassword().equals(password)`).

**Finding.** Passwords are persisted and compared as raw strings; comparison is non-constant-time.

**Why this is critical.** Any DB leak (backup, SQL injection elsewhere, an admin with DB access, a misconfigured dump) exposes every account instantly and, via password reuse, users' other accounts. For a financial product this is an automatic compliance and reputational failure.

**Required changes.**
1. Hash with **Argon2id** (preferred; OWASP minimum m=19 MiB, t=2, p=1) or BCrypt (cost ≥ 12; note the 72-byte input limit; enforce max length or pre-hash). Use Spring Security's `PasswordEncoder` (`DelegatingPasswordEncoder` so algorithms can be upgraded).
2. Column becomes `password_hash` (length ≥ 255). Never log, return, or serialize it.
3. **Migration for existing users:** one-time migration job that hashes existing plaintext values in place and then overwrites the plaintext column (drop it). Because those passwords were exposed in logs/URLs/`sessionStorage`, additionally **force a password reset at next login** for all existing accounts, and block withdrawals for 24–72 h after a forced reset (see H22 cooldown).
4. Constant-time verification via the encoder's `matches`. For nonexistent users, run a dummy hash verification so login timing does not reveal whether the username exists.
5. `changePassword`: require the current password, hash the new one, and invalidate all other sessions (H9).

**Acceptance criteria.**
- DB contains no plaintext passwords (assert in a migration test).
- Login of nonexistent vs wrong-password users takes statistically similar time and returns an identical error message.
- A password-hash upgrade path is covered by a test (`DelegatingPasswordEncoder` `upgradeEncoding`).

---

### C4. Credentials live on the client and travel in URLs

**Location:** `app.js` (`saveSession` stores `{username, password}` in `sessionStorage`; `syncBalanceFromGet`, `syncItemsFromGet`, tab handlers, `checkAndRestoreActiveDeposit`, `restoreSessionIfPresent` put `username` and `password` into the **query string**; profile "Show password" button), `UserController` (`GET` endpoints with `@RequestParam password`, and `@RequestBody` on `GET`).

**Finding.** The password is sent with every request, in plain text, including in URLs; and it is kept in JS-accessible storage and shown on screen.

**Why this is critical.** URLs are written to server access logs, reverse-proxy logs, CDN logs, browser history, and `Referer` headers. A password in a URL is effectively published to everyone with log access. In `sessionStorage` it is readable by any XSS (C2), by browser extensions, and by anyone with physical access. There is no way to revoke a session without changing the password. The "Show password" feature should not exist: the client should not know the password after login.

**Required changes.**
1. Introduce real authentication: **server-side session** (opaque random token, 256-bit, stored hashed server-side, with TTL) or short-lived JWT + refresh token. Deliver it as a cookie with `HttpOnly; Secure; SameSite=Strict; Path=/`.
2. Because a cookie session is used, add **CSRF protection** for all state-changing requests (double-submit token or Spring Security CSRF; `SameSite=Strict` is defense in depth, not the only control).
3. `POST /api/users/login` and `POST /api/users` establish the session; the response must not echo credentials. The password is never stored client-side, never in URLs, never in logs.
4. Remove `username`/`password` from every request body and query string, and remove all `GET` endpoints that take credentials. The principal comes from the session.
5. Remove the "Show password" UI and `currentUser.password`; the "change password" flow asks the user to type their current password.
6. `restoreSessionIfPresent` calls a `GET /api/me` that relies on the cookie only.
7. Logging: add a log filter that redacts `password`, `newPassword`, `Authorization`, `Cookie`, `X-Admin-Key`; ensure request logging never records query strings with secrets.
8. Prefer Spring Security (filter chain, `SecurityContext`, method security) over hand-rolled interceptors.

**Acceptance criteria.**
- No request in the app contains `password` in a URL (assert in an E2E test by inspecting network traffic).
- `document.cookie`/`sessionStorage`/`localStorage` contain no credential after login.
- Cross-site POST without CSRF token is rejected (integration test).

---

### C5. Race conditions on balance and inventory (double-spend / duplicated payouts)

**Location:** `UserService.buyItem`, `UserService.openCase` (use `authenticateOrFindUser`, i.e. **no row lock**); `CaseOpeningService.buyCase`, `openCaseForUser`; `User.items` as `@ElementCollection List<String>`; `User` has no `@Version`.

**Finding.** `instantOpenCase` correctly uses `SELECT … FOR UPDATE` on the user, but `buyItem` and `openCase` do not. Two parallel `open` requests for the same crate both read the same inventory list, both pass the ownership check, both remove one item, both spin, and both credit the payout. For `ElementCollection` lists Hibernate rewrites the collection (delete + reinsert), so concurrent writers overwrite each other (lost updates). `buyItem` can overspend the balance or lose a balance update the same way.

**Why this is critical.** Direct money duplication: with N parallel requests one owned crate yields N payouts. Trivially scriptable. The bankroll lock is per spin, not per user, so it does not prevent this.

**Required changes.**
1. **All** operations that read-then-write `balance` or `items` must load the user via `findByUsernameForUpdate`/`findByIdForUpdate` in the same transaction. Establish and document a **global lock order** (e.g. user → bankroll; transaction row → user) and keep it everywhere to avoid deadlocks (note `approveTransaction` locks tx then user; keep it consistent).
2. Add `@Version` (optimistic locking) to `User` as a second safety net, and map `OptimisticLockingFailureException` to HTTP 409 with a safe retry message.
3. **Redesign inventory storage:** replace `@ElementCollection List<String>` with a table `user_cases(user_id, case_id, quantity)` with `UNIQUE(user_id, case_id)` and `CHECK(quantity >= 0)`. Decrement atomically (`UPDATE … SET quantity = quantity - :n WHERE user_id = :u AND case_id = :c AND quantity >= :n`, assert affected rows = 1).
4. Add DB-level protection: `CHECK (balance >= 0)` on `users.balance`.
5. Move balance mutation behind one service (see H14 ledger) so the pattern is not repeated per endpoint.
6. Combine with idempotency (H19) so client retries cannot double-execute.

**Acceptance criteria.**
- Concurrency integration test (real DB, Testcontainers): 20 parallel `open` calls for a user owning 1 crate → exactly 1 succeeds, 19 get 4xx; final balance equals initial + exactly one payout.
- Same for parallel `buy` with a balance sufficient for one purchase.
- Final invariant tests (H15) hold after the concurrency tests.

---

### C6. Admin access control is weak

**Location:** `AdminAuthInterceptor`, `AdminController.login`, `UserController.verifyPromo`, `UserService` (`@Value("${slots.admin.password:admin1234}")`), `app.js` `handlePromoSubmit`, `admin.js` (`sessionStorage.adminKey`, `X-Admin-Key` header).

**Finding.**
- A single shared static password protects the entire admin surface (approve/reject real withdrawals, bankroll reset).
- That same password can be brute-forced through three unthrottled entry points: `/api/admin/login`, `/api/users/promo` (a public oracle that confirms the password), and any admin endpoint via `X-Admin-Key`/`Bearer`.
- The `ADMIN_SESSION` cookie **contains the admin password itself**, and the password is also copied into `sessionStorage` (readable by JS, defeating `HttpOnly`).
- `UserService` falls back to `admin1234` if the property is missing; the interceptor fails closed in the same situation. Inconsistent and dangerous.
- The admin login is hidden behind a "promo code" field in the player UI. That is obscurity, not security; and it makes players' browsers type the admin password into a public page.
- No audit trail of who approved what; no second factor; no separation of duties.

**Why this is critical.** Admin = control of funds. Compromise of one string = loss of all funds and total data exposure; combined with C2 it needs no password guessing at all.

**Required changes.**
1. Create **admin accounts** (table `admin_users`: id, login, password hash, TOTP secret, role, active). Roles at least: `SUPPORT` (read), `FINANCE` (approve/reject), `OWNER` (bankroll adjustments, admin management).
2. **Mandatory 2FA (TOTP)** for admin login. Admin sessions are separate from player sessions: random opaque token in an `HttpOnly; Secure; SameSite=Strict` cookie, short idle timeout (e.g. 15–30 min), absolute timeout (e.g. 4 h). **The cookie must never contain the secret itself.**
3. Remove: the `slots.admin.password` mechanism, `X-Admin-Key`/`Bearer` for browsers, `sessionStorage.adminKey`, `/api/users/promo`, the promo UI in the player profile, and the `admin1234` default. The application must **fail to start** if admin bootstrap secrets are missing in production.
4. Serve `/admin` and `/api/admin/**` on a separate hostname or path restricted by **IP allowlist/VPN** at the proxy level, in addition to app-level auth.
5. Strict rate limiting + lockout on admin login (H10).
6. **Admin audit log** (append-only): admin id, action, target (tx id, user), before/after, IP, user agent, timestamp. Log all approve/reject/reset/view-user actions.
7. **Four-eyes principle** for withdrawals above a configurable threshold (a second admin must approve), and daily withdrawal caps requiring `OWNER`.
8. Mandatory confirmation + reason for destructive actions (see H15 for bankroll operations).

**Acceptance criteria.**
- No code path accepts a shared static key. Admin endpoints return 401/403 without a valid admin session with the required role.
- Test: 10 failed admin logins from one IP/account trigger throttling/lockout.
- Every state-changing admin action produces an audit row (asserted in tests).
- The admin cookie value is unrelated to any credential (random token).

---

### C7. `deleteAccount` destroys financial records and money

**Location:** `UserService.deleteAccount` (`transactionRepository.deleteByUser(user); userRepository.delete(user);`), no locking, no state checks.

**Finding.** The account and **all** of its transactions (including PENDING withdrawals and approved deposit history) are hard-deleted. The user's remaining balance is destroyed. No user lock is taken, so it races with deposits/withdrawals/openings.

**Why this is critical.** Money disappears with no trace: a PENDING withdrawal that the admin already paid out on-chain loses its record; deposits lose their evidence. Financial and AML audit trails must be retained. Combined with C1, an unauthenticated attacker can wipe a victim's account (delete requires only the username when password is optional elsewhere; after C1 it still needs correct auth, but the data-destruction design remains wrong).

**Required changes.**
1. Replace hard delete with **soft delete / deactivation**: `users.status` (`ACTIVE`, `DELETED`), `deleted_at`; keep the row, transactions, ledger, spin logs. Anonymize PII only if legally required while keeping a pseudonymous id.
2. Block deletion when the user has any `PENDING`/`AWAITING_PAYMENT` transaction or a non-zero balance (instruct the user to withdraw first), or implement an explicit "withdraw remaining balance then delete" flow.
3. Require fresh re-authentication (password + optional 2FA/email confirmation) and a cooldown/confirmation step.
4. Reserve the username (do not allow re-registration of a deleted username → prevents impersonation and transaction ambiguity).
5. FK constraints `ON DELETE RESTRICT` between `balance_transactions`/ledger and `users`. Remove `deleteByUser` entirely.
6. Lock the user row during the operation; invalidate all sessions.

**Acceptance criteria.**
- Deleting an account never removes `balance_transactions`, ledger or spin rows (asserted).
- Deletion fails with a clear 409 when balance > 0 or a PENDING tx exists.
- A deleted username cannot be registered again; deleted users cannot log in.

---

### C8. Deposits are not verified; deposit/withdraw plumbing is unsafe

**Location:** `DepositSessionResponse` (`DEFAULT_ADDRESS` — one static address for everyone, also hardcoded twice in `app.js`), `UserService.startDepositSession` / `confirmDepositSent` / `approveTransaction`, `BalanceTransaction.txHash` (random internal id), `withdraw` (`walletAddress` only checked non-blank).

**Finding.**
- Every user sends to the same address with no per-user identifier (no memo/comment or derived address), so an incoming payment cannot be attributed to a user.
- "Sent" is a button click; no on-chain proof. An admin approves manually by trusting the user.
- `txHash` is an internal random value, not a blockchain transaction hash, and its name is misleading.
- The deposit address is duplicated in backend and frontend code.
- Withdrawal addresses are unvalidated free text (up to 256 chars; a longer string causes a 500).

**Why this is critical.** A single admin mis-click (or a social-engineering attack: "I sent it, please approve") credits real money that never arrived, or credits the wrong user. There is no reconciliation between what the platform believes and what the wallet holds. Manual attribution does not scale and is error-prone; each mistake is a direct financial loss.

**Required changes.** (Direction; exact provider/integration is a product decision to confirm with the owner.)
1. **Per-deposit attribution:** use a unique **comment/memo per deposit session** (TON supports a text comment on transfers) or a unique deposit address per user/session derived from an HD wallet. The memo must be unguessable and bound to the session row.
2. **On-chain verification:** a backend poller/webhook (TON indexer such as toncenter/tonapi or your own node) detects incoming USDT jetton transfers to the platform address, matches memo + amount + minimum confirmations, and records the **on-chain transaction hash** (unique constraint → a given on-chain tx can credit only once). Credit automatically and idempotently. Keep manual review only for edge cases (wrong amount, missing memo, expired session) in a separate "unmatched deposits" admin queue.
3. Rename fields: internal `txHash` → `publicId`/`referenceId`; add `chainTxHash` (nullable, unique). Update DTOs and admin UI accordingly.
4. Move the deposit address (and chain/currency) into **one configuration source** (`application` properties, exposed via `/api/config`); remove the hardcoded copies in `DepositSessionResponse` and `app.js`.
5. **Withdrawals:** validate the TON address format (including checksum/flags), length limit, and network; show the user the address back for confirmation; store normalized form. Withdrawal payouts should be executed from a **separate hot wallet with limits**, with the main treasury kept cold; define per-transaction and daily limits (see H15/Section on limits).
6. Handle expiry/late payments: a payment arriving after the session expired must land in the unmatched queue, not silently vanish.
7. Add a daily reconciliation job (H15).

**Acceptance criteria.**
- A deposit credits the user only after an on-chain transfer with the correct memo/amount is observed (test with a mocked indexer); replaying the same chain tx credits once.
- No hardcoded wallet address remains outside configuration.
- Invalid/oversized wallet addresses return 400 (never 500).

---

## HIGH

### H9. No real session lifecycle

**Location:** `UserService.logoutUser` (authenticates and returns a message; changes nothing), no token/session store.

**Finding / why bad.** Logout is cosmetic; credentials remain valid forever; there is no way to revoke access after a suspected compromise; password change does not terminate other sessions.

**Required changes (builds on C4).** Server-side session store (DB/Redis) with: idle timeout, absolute timeout, **rotation on login and on privilege/password change**, revoke-on-logout, revoke-all on password change, optional "active sessions" list. Session ids hashed at rest. `logout` deletes the session and clears the cookie.

**Acceptance criteria.** After logout (or password change) the previous session cookie receives 401; sessions expire per policy (tested with a clock).

---

### H10. No rate limiting or brute-force protection

**Location:** all endpoints; especially `login`, `createUser`, `promo`, `deposit/start`, `withdraw`, `admin/login`.

**Finding / why bad.** Unlimited password guessing, unlimited account creation (spam, enumeration, DB growth), unlimited requests that take pessimistic locks (cheap DoS against the single bankroll row), and the admin-password oracle (C6).

**Required changes.**
- Rate limits per IP **and** per account/identifier (Bucket4j, Redis, or proxy-level). Suggested starting points: login 5/min per account+IP, 20/min per IP; register 3/hour per IP; money endpoints 10–30/min per user; admin login 5/10 min.
- Progressive delays/temporary lockouts that do not allow trivially locking out a victim (key by account **and** IP combination; CAPTCHA after N failures).
- Resolve the real client IP correctly behind the reverse proxy (trusted proxy list; `server.forward-headers-strategy`). Never trust raw `X-Forwarded-For` from arbitrary clients.
- Return 429 with `Retry-After`. Log and alert on spikes.

**Acceptance criteria.** Automated tests prove throttling triggers; load test shows `open` spam cannot starve the bankroll lock for other users beyond a defined bound.

---

### H11. Public user list / leaderboard data exposure

**Location:** `UserController.getAllUsers` (`GET /api/users`), `getTopRichUsers` (`/api/users/top`), eager loading in `User`.

**Finding / why bad.** Anyone can download all usernames, balances and inventories (this is the target list for C1 exploitation and for social engineering). `findAll()` with `EAGER` collections and no pagination is also a DoS vector as the table grows.

**Required changes.** Remove `GET /api/users` (or make it admin-only, paginated, projection-based). Remove `/api/users/top` or return only a pseudonymous alias and rounded value; confirm with the product owner whether a public leaderboard is wanted at all. Replace `FetchType.EAGER` with lazy loading + explicit fetch/projections. Similarly review the live feed (H-level privacy: usernames in public feed — allow a "hide my name" setting or mask names, e.g. `ch***id`).

**Acceptance criteria.** No unauthenticated endpoint returns user lists/balances; the pagination limit is enforced server-side (max page size).

---

### H12. Effective RTP differs from the advertised RTP (math/fairness)

**Location:** `CaseOpenerEngine.drawOutcome` (filters paytable by `winAmount <= prizePool + floorBuffer`, then renormalizes), `CaseOpeningService.openWithPaytable`, `GET /api/cases/math` (static RTP), `BankrollSplit` (`HOUSE_RATE = 7%`).

**Finding.**
1. When the prize pool is low, expensive outcomes are removed and the remaining weights are renormalized. The **actual** RTP then depends on the pool state (i.e. on other players' recent results) and is lower than the nominal paytable RTP. `/api/cases/math` and the inspect modal show the nominal figures.
2. With a 7% house inflow, sustainable payout is ≤ 93% of volume. The engine's javadoc states "RTP 92–94%", and a paytable with nominal RTP > 93% cannot be sustained; it will be silently distorted by the filter.
3. `resetBankroll` (C-level admin action) instantly changes every player's odds.
4. The frontend roulette builds the reel with `Math.random` from the static table, visually implying a distribution the server did not use.

**Why this is bad.** Misrepresenting odds is a legal/regulatory and trust problem for a gambling product, and it also makes the platform's financial model unpredictable. It also enables strategies for informed players (play when the pool is high).

**Required changes.**
1. Make a **policy decision with the owner** and document it: either (a) odds are state-dependent by design and must be clearly disclosed with the *effective* RTP range, or (b) redesign so nominal odds are honored (e.g. adequate seed capital / pool floor so the filter essentially never triggers, plus alerting if it does).
2. Add a **Monte-Carlo simulation test/tool** (≥ 10M spins per case) with the real pool dynamics from several initial pool states (0, seed, high) that reports effective RTP, pool depletion probability and filter activation rate per case. Fail CI if effective RTP deviates from nominal beyond a configured tolerance in the "normal" state.
3. Record, per spin, whether the filter removed any outcomes (see H14 spin log) and expose a metric "filter activation rate"; alert if above threshold.
4. Enforce nominal RTP ≤ `100 − HOUSE_RATE·100` (see H13).
5. Server returns the exact reel layout/near-miss items it used, or the reel is made purely cosmetic and documented as such; the winning index logic stays server-driven.

**Acceptance criteria.** Simulation report attached to the PR; metrics exist; documented policy decision.

---

### H13. `validateMathematics` does not validate RTP

**Location:** `CaseOpenerEngine.validateMathematics`, `CaseInitialCatalogConfig`.

**Finding / why bad.** Only `Σp = 1` (tolerance 1e-4) and "has a floor item" are checked; javadoc claims RTP (92–94%) validation. A case can be seeded with RTP 130% or 60% and start losing/stealing money immediately. A `1e-4` tolerance also allows probability mass drift.

**Required changes.**
- Enforce: `Σp == 1` within a tight tolerance (use `BigDecimal` or ≤ 1e-9); `0 < p ≤ 1`; multipliers ≥ 0; at least one floor item; **RTP within configured `[min, max]` and ≤ 93%** (derived from `HOUSE_RATE`); max single payout ≤ configured cap relative to expected pool; unique tier labels. Calculate EV with `BigDecimal` rather than `double`.
- Run validation on **every** write path (seeder, any future admin case editor), not only startup, and fail loudly (exception, not `System.err`).
- Add unit tests with malicious/bad paytables.

**Acceptance criteria.** Table-driven tests reject RTP 130%, 60%, Σp = 0.9999, negative multiplier, no-floor tables.

---

### H14. No ledger, no spin history, no audit trail

**Location:** `User.balance` is mutated via `setBalance` in many places; no spin table; live feed is an in-memory 2-item deque in `UserService`.

**Finding / why bad.** There is no way to answer "why is this user's balance X?", to prove a payout was legitimate, to investigate disputes/fraud, or to reconcile with the wallet. A server restart loses the live feed. Multiple instances would each have a different feed.

**Required changes.**
1. **Append-only `ledger_entries`:** id, user_id, entry_type (`DEPOSIT`, `WITHDRAW_REQUEST`, `WITHDRAW_REFUND`, `CASE_PURCHASE`, `CASE_OPEN_COST`, `CASE_PAYOUT`, `ADJUSTMENT`, …), signed amount, `balance_after`, reference (tx id, spin id), idempotency key, created_at. **All** balance changes go through a single `LedgerService` that writes the entry and updates the balance in the same transaction. DB role should have no UPDATE/DELETE on this table.
2. **`spin_log`:** spin_id, user_id, case_id, paytable version id (see H18), price, outcome tier/multiplier/amount, pool_before, pool_after, floor_buffer, house_inflow, profit_inflow, filtered-out outcomes flag, server-seed reference (H17), request id, IP, created_at.
3. **Live feed** is derived from `spin_log` (after commit, not before), not from process memory; cache with a short TTL. Delay the feed entry until the client animation would have finished to avoid spoilers, or include only completed spins.
4. Integrity: add a nightly job verifying `Σ ledger = balance` per user.

**Acceptance criteria.** Every balance mutation test asserts a ledger entry; `balance == Σ ledger` property holds after the concurrency tests; restart does not lose the feed.

---

### H15. No reconciliation with real funds; destructive `resetBankroll`

**Location:** `CaseOpeningService.resetBankroll`, `AdminController.resetBankroll`; no reconciliation code anywhere.

**Finding / why bad.** The platform tracks virtual numbers only. `resetBankroll` zeros the prize pool and house profit, destroying accounting and instantly altering player odds (H12). A single admin action (or an XSS/stolen admin session) can erase the financial record.

**Required changes.**
1. Replace "reset" with explicit, audited operations: `POOL_TOPUP` (owner adds funds, with reference), `PROFIT_WITHDRAWAL` (house profit paid out, with on-chain reference), each producing ledger/audit entries and requiring `OWNER` role + 2FA re-check + reason text. Remove the endpoint that zeros values.
2. **Invariant** to be computed and exposed on an admin dashboard and checked by a scheduled job (alert on drift):

   `Σ user.balance + Σ PENDING withdrawals + prizePool + houseProfit − initialPoolSeed = Σ APPROVED deposits − Σ APPROVED withdrawals`

   (A spin is zero-sum across user/pool/house by construction: `−C + win + (0.60C + 0.33C − win) + 0.07C = 0`, so this must always hold; any mismatch indicates a bug or fraud.) Account for pool top-ups/profit withdrawals as explicit adjustments.
3. Compare the above with the **actual on-chain wallet balance(s)** daily; alert on discrepancy beyond a threshold.
4. Define and enforce limits: per-transaction and daily withdrawal caps; cooldown after password change (H22); cap on `PENDING` age (see Medium list; add TTL/auto-expiry with refund for old pending withdrawals only through admin action).

**Acceptance criteria.** Invariant test passes after randomized sequences of deposits/withdrawals/spins/rejections (property-based test); reset endpoint is gone; reconciliation job exists with an alert.

---

### H16. Input validation on amounts, addresses and DTOs

**Location:** all request DTOs (`BalanceOperationRequest`, `ItemOperationRequest`, …), `UserService.validatePositiveAmount`, `withdraw`, entity column lengths/precisions.

**Finding / why bad.**
- No Bean Validation. Amounts accept any scale (JS sends floats such as `0.30000000000000004`); DB columns are `scale 2`, so values are silently rounded or rejected inconsistently.
- No maximum amount; huge values overflow `precision 19` → 500 errors; extreme values can be abused to break logic.
- `walletAddress` has no format check and no length limit matching the 256 column.
- Usernames/slugs have no length constraints matching the DB columns.
- Error messages from Jackson are returned to clients (`GlobalExceptionHandler.handleNotReadable`), leaking internals.

**Required changes.**
- Add `@Valid` + Jakarta Validation on every DTO: `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`, `@DecimalMax`, `@Digits(integer=…, fraction=2)`; reject (do not round) amounts with more than 2 decimals.
- Frontend sends amounts as strings with ≤ 2 decimals (or integer minor units/cents); backend uses `BigDecimal` only. Consider storing money in minor units.
- Server-side configurable `MIN`/`MAX` per operation; single source of truth for limits via `/api/config`; remove duplicated literals ("$1.00", "$2.50") in messages and in `app.js`.
- Validate wallet address (see C8), length, charset.
- DB `CHECK` constraints (`amount > 0`, `balance >= 0`, `quantity >= 0`).
- Harden `GlobalExceptionHandler`: handle `MethodArgumentNotValidException`, `OptimisticLockingFailureException` (409), `DataIntegrityViolationException`, `IllegalStateException`, and a final `Exception` catch-all returning a generic message plus a correlation id; log details server-side only.

**Acceptance criteria.** Fuzz/table tests: huge numbers, negative, zero, 3+ decimals, NaN strings, over-long strings all yield 400 with sanitized messages; no 500s from user input.

---

### H17. No provably fair mechanism

**Location:** `CaseOpenerEngine` (`SecureRandom`), client-side reel generation.

**Finding / why bad.** Players cannot verify that outcomes weren't chosen adversarially. For a real-money drop/case product this is both a trust and a regulatory expectation. Operators also lack proof to resolve disputes.

**Required changes.**
- Implement commit–reveal: per user, a server seed is generated, its **SHA-256 hash is shown in advance**; the player can set/rotate a client seed; each spin uses `HMAC_SHA256(serverSeed, clientSeed:nonce)` → uniform value → outcome; the old server seed is revealed on rotation so spins can be re-verified. Provide a public verification page/endpoint and documentation of the algorithm.
- **Important interaction with the dynamic filter (H12):** the derived uniform value must be applied to the *active pool snapshot*, so the spin log must store the filtered paytable (or the pool state and rules) so the result can be reproduced independently.
- Store seed commitments and nonces in `spin_log` (H14). Keep server seeds secret (encrypted at rest) until reveal.

**Acceptance criteria.** An independent verifier script reproduces historical outcomes from logs and revealed seeds (test included).

---

### H18. Seeder silently rewrites live paytables

**Location:** `CaseInitialCatalogConfig.seedInitialCases` (updates `paytableJson` of existing cases at every startup when `initial-cases.json` differs; does not update price/name/active).

**Finding / why bad.** A deployment can change odds of live cases without an audit record, a version, an approval or a notice to players; behavior is inconsistent (paytable updates, price doesn't). A bad JSON edit changes live money math instantly.

**Required changes.**
- The seeder only **inserts missing** cases; it must never update existing rows (log a warning on drift and fail startup in production, if desired).
- Introduce `case_paytable_versions` (version id, case id, paytable JSON, RTP stats, created_by, created_at, approved_by, effective_from). Cases reference the active version; each spin stores the version id (H14).
- Paytable changes go through an audited admin flow (validated with H13, simulated per H12, requires `OWNER` role + approval).

**Acceptance criteria.** Restarting the app with a modified `initial-cases.json` does not change existing cases; paytable changes are visible in version history with author and time.

---

### H19. No idempotency for money-changing operations

**Location:** `buyItem`, `openCase`, `instantOpenCase`, `startDepositSession`, `confirmDepositSent`, `withdraw`; frontend buttons without in-flight guards (`BUY`, `Instant Open`, withdraw "Sent").

**Finding / why bad.** Double click or network retry (timeout after the server already processed) executes the operation twice: double purchase, double withdrawal, or the client not knowing the outcome and the user asking for refunds. Network errors on the instant-open flow are swallowed by `catch (_) {}` and leave the client UI inconsistent.

**Required changes.**
- Require an `Idempotency-Key` header (UUID generated per user action) on all money-changing POSTs. Store `(user_id, key, request_hash, response_body, status, created_at)` with a unique constraint; a repeated key returns the stored response; same key with a different body → 409/422; TTL 24 h.
- Frontend: generate a key per click, disable the button during the request, and on network failure re-sync state from the server (balance/inventory/spin history) instead of silently ignoring errors; show an error to the user.

**Acceptance criteria.** Test: sending the same request twice with the same key yields one state change and identical responses; concurrent duplicates execute once.

---

### H20. Missing security headers, CORS/proxy configuration, cookie flags

**Location:** application/web config not visible in the reviewed files (`WebConfig` only registers the admin interceptor); `AdminController` cookie uses `secure(request.isSecure())`.

**Finding / why bad.** No CSP (so XSS in C2 is exploitable), no frame protection (clickjacking of the admin panel), no HSTS. Behind a TLS-terminating proxy `request.isSecure()` is `false` unless forwarded headers are configured, so cookies may be issued without `Secure`. CORS rules are unknown and must not allow credentialed wildcard origins.

**Required changes.**
- Add on all responses: `Content-Security-Policy` (see C2), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` (or `frame-ancestors 'none'`), `Referrer-Policy: no-referrer`, `Strict-Transport-Security` (preload-ready once HTTPS is final), `Permissions-Policy` (disable unused features), `Cache-Control: no-store` on authenticated API responses.
- Set `server.forward-headers-strategy=framework` (or equivalent) and configure the proxy to pass `X-Forwarded-*`; always set `Secure` on cookies in production (config flag, not `isSecure()`).
- Explicit CORS allowlist (exact origins, no `*` with credentials) or disable CORS if the frontend is same-origin.
- Disable Spring Boot Actuator endpoints that leak data or protect them; hide stack traces (`server.error.include-*=never`); remove server version banners.
- Enforce HTTPS only; redirect HTTP → HTTPS.

**Acceptance criteria.** An automated header test (or `securityheaders`-style check in CI) passes; cookie flags verified in integration tests with `X-Forwarded-Proto: https`.

---

### H21. Weak username and password policy; user enumeration; impersonation

**Location:** `UserService.createUser`, `validateCredentialsInput`, `findUserByUsername` (404 vs 401 messages), `User.username` (case-sensitive unique), `app.deposit.admin-username` (`chromakid`).

**Finding / why bad.**
- A one-character password is accepted.
- Usernames are case-sensitive and unrestricted: `ChromaKid`/`chromakid`, Unicode homoglyphs (Cyrillic `а` vs Latin `a`), invisible characters allow **impersonating the deposit admin or other players** (a social-engineering vector for deposits).
- 404 "User not found" vs 401 "Invalid password" lets attackers enumerate accounts.

**Required changes.**
- Normalize usernames (Unicode NFKC, trim, lowercase) into a `username_normalized` column with a unique index; allow only `^[a-z0-9_]{3,20}$`; reject confusable/mixed-script names; maintain a **reserved list** (admin, support, the configured deposit admin username, staff, moderator, etc.).
- Password policy per NIST 800-63B: min length 10–12, max ≥ 64, no composition rules, check against a breached-password list (e.g. k-anonymity HIBP or a local list), disallow password == username.
- Uniform error: "Invalid username or password" for both unknown user and wrong password, with equal timing (C3).
- Registration cannot avoid revealing taken usernames, so protect it with rate limiting/CAPTCHA (H10).

**Acceptance criteria.** Tests: homoglyph/case variants of reserved/existing names rejected; weak/breached passwords rejected; identical response bodies and similar timing for unknown vs wrong credentials.

---

### H22. No account recovery and no withdrawal safety net on credential changes

**Location:** n/a (feature missing); `changePassword`, `withdraw`.

**Finding / why bad.** A forgotten password means the user permanently loses access to funds (leading to support disputes/chargebacks of reputation). Conversely, once recovery is added (or any password change happens), an attacker who takes over the account can immediately withdraw.

**Required changes (product decision required on the recovery channel).**
- Add an optional-but-encouraged **verified contact** (email or Telegram) and a recovery flow: single-use, short-lived, hashed reset tokens; rate-limited; no account enumeration in responses. Alternative if no contact is desired: a one-time recovery phrase shown at registration (user's responsibility).
- Security notifications to the verified contact on login from a new device, password change, and withdrawal request.
- **Cooldown:** block withdrawals (e.g. 24–72 h) after password change, recovery, or contact change; optionally require 2FA (TOTP) for withdrawals.
- Withdrawal confirmation step via the verified channel for amounts above a threshold.

**Acceptance criteria.** Tests: reset tokens are single-use/expiring; withdrawals are rejected within the cooldown after a password change; no enumeration via the recovery endpoint.

---

## Delivery Plan (recommended order)

| Phase | Items | Notes |
|---|---|---|
| **0 (immediately)** | Withdrawal kill-switch, Section 0.1 incident review | Before any code change is deployed |
| **1 (days)** | C1, C2, C5, C7 | Small, independent of the architecture rewrite; ship together with tests |
| **2 (week 1)** | C3, C4, C6, H9, H10, H20, H21 | Auth/session architecture (Spring Security), admin accounts + 2FA, headers, rate limits |
| **3 (week 2)** | C8, H14, H15, H16, H19, H11 | Ledger, deposits verification, reconciliation, validation, idempotency |
| **4** | H12, H13, H17, H18, H22 | Math integrity, provably fair, paytable versioning, recovery |
| **5** | Refactor (split `UserService` into `AuthService`, `WalletService`, `AdminService`, `LiveFeedService`; modularize `app.js`) | Only after phases 1–3 are green |

## Global Definition of Done

- All acceptance criteria above pass in CI (unit, integration with real DB via Testcontainers, concurrency tests, E2E for XSS/CSP and session behavior).
- Migrations applied cleanly on a copy of production data; rollback plan documented.
- A security regression test suite exists: unauthenticated access matrix, injection/XSS payloads, rate limits, idempotency, race conditions.
- Dependency scanning (OWASP Dependency-Check / Dependabot) and secret scanning enabled in CI.
- Documentation: architecture of auth/sessions, ledger and invariants, admin runbook (approve/reject, reconciliation, incident response), list of config properties and their secure defaults.
- Final report from the implementer: for each item ID, what changed, where, how it was tested, and any deviations or open questions for the owner.

## Out of Scope (flagged for the owner)

- Legal/regulatory: gambling licensing, age verification, KYC/AML, jurisdiction restrictions, responsible-gambling tools. These are required before real-money launch but are not code tasks.
- Medium/low findings (PENDING TTL, N+1/EAGER cleanup, logging/metrics beyond what is listed, `UserService` decomposition, frontend modularization) are handled after Phase 3.
