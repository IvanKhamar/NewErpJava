# Features built in .NET first, to port here

The .NET version (`IvanKhamar/NewErp`, locally `F:\PROJECTS\NewErp\NewErp`) sometimes gets a feature before this repo.
Each such feature is one entry below, written by whoever built it in .NET, so the port starts from a ready plan.

How to use this file:

- **Porting an entry:** read the .NET code and tests it names, keep the contract exactly (paths, JSON fields, status
  codes, Ukrainian texts, config defaults), and port the tests as JUnit tests. One entry = one PR here.
- **When it is merged here:** change its status to `DONE`, with the PR number and date. Do not delete entries: they are
  the history of what was synced.
- **Adding an entry (from the .NET side):** newest at the bottom, with the same headings. Status `TODO` until ported.
- Features that went the other way (Java first, then .NET) are not listed here; they are in Phase 16 of the plan doc.

---

## 1. Security hardening: login throttle and security headers

- **Status:** TODO
- **.NET:** branch `chore/security-hardening` (2026-10-29), commit f0eaeb8. Check the PR number in NewErp once it is opened.
- **Why:** both repos are public and both APIs must answer the same way, so the Blazor UI behaves the same on either.

### Contract (must match exactly)

`POST /auth/login`:

- Only **failed** attempts are counted, in two counters: per login (trimmed, lower-case) and per client IP.
- When a login has **10** failures, or an IP has **30**, within a **15-minute** window that starts at the first failure,
  the next attempt (even with the right password) is refused until the window ends:
  - status **429**, header `Retry-After: <seconds, rounded up>`;
  - ProblemDetails `title` = `Забагато невдалих спроб входу`,
    `detail` = `Забагато невдалих спроб входу. Спробуйте знову через {N} хв.` (N = minutes left, rounded up).
- The check happens **before** the password is verified. A failed attempt adds 1 to both counters. A successful login
  resets the login counter only (the IP counter stays, so guessing other logins is not reset).
- After the window ends, counting starts again from 1.
- Wrong password stays as before: 401, title `Невірний логін або пароль`.
- **Same time for unknown logins:** if the login does not exist, still hash the given password once (the same cost as
  verifying), and verify the password of a disabled user before refusing. Then the response time does not reveal which
  logins exist.
- Config (.NET `LoginThrottle:*`; suggested here `login-throttle.*`): `max-failures-per-user` 10,
  `max-failures-per-address` 30, `window-minutes` 15.
- Counters live in memory (one process); cap the map (the .NET one prunes expired entries at 10 000 and clears it if it
  is still full), so thousands of invented logins cannot eat the memory.

Headers on **every** response:

| Header | Value |
| --- | --- |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |
| `Cross-Origin-Opener-Policy` | `same-origin` |
| `Content-Security-Policy` | for the API: `default-src 'self'; script-src 'self' 'unsafe-inline' 'wasm-unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'` |

- No `Server` header. HSTS outside dev. The session cookie is `Secure` outside dev and tests.
- The Swagger/OpenAPI UI is left without CSP (in .NET: `/scalar`, Development only).
- **Vaadin UI:** Vaadin may need a different CSP (it uses inline scripts and styles; check the Vaadin docs before
  tightening). Keep the other headers. Check in a real browser that the Vaadin UI still loads and works after login.

### Also in that .NET change (repo hygiene, do the same here)

- CI job that scans every push and PR for secrets: `gitleaks/gitleaks-action@v2` with `fetch-depth: 0` and
  `GITHUB_TOKEN`. A test value it flags by mistake goes into `.gitleaksignore` with a comment.
- `.github/dependabot.yml`: monthly `maven` and `github-actions` updates, minor and patch grouped in one PR.
- Fail the build on a dependency with a known vulnerability (OWASP dependency-check or similar; .NET uses NuGet audit).
- A guard test that SQL is never built by joining strings: .NET has `RawSqlGuardTests` (raw SQL only in an allowlist of
  files that pass ints). Here: no string concatenation into `JdbcClient.sql(...)`; values only via `.param(...)`.
- `.gitignore`: `.env.*`, `secrets.json`, `*.pfx`, `*.p12`, `*.pem`, `*.key`, local config overrides.
- `CLAUDE.md` rules on secrets (see the "Безпека" section of the .NET `CLAUDE.md`).

### Tests to port (the specification)

- `tests/NewErp.UnitTests/Application/LoginThrottleTests.cs`: block after the per-login limit until the window ends
  (with a fake clock), the same login written differently counts together, the IP limit across many logins, a success
  resets only the login counter.
- `tests/NewErp.IntegrationTests/SecurityApiTests.cs`: the headers on `/health`; with the per-login limit set to 3:
  three 401s, then 429 with `Retry-After` and that title even with the right password, while another user still logs in.
- `tests/NewErp.Web.Tests/LoginTests.cs` → `TooManyFailures_ShowsTheServerExplanation`: the login page shows the
  server's `detail` on 429 (not "server unavailable"). In Vaadin: the login view shows the same message.

### .NET files for reference

`src/NewErp.Application/Auth/LoginThrottle.cs`, `src/NewErp.Application/Auth/AuthService.cs` (LoginAsync),
`src/NewErp.Api/Endpoints/AuthEndpoints.cs`, `src/NewErp.Api/SecurityHeaders.cs`, `src/NewErp.Api/Auth/AuthSetup.cs`,
`src/NewErp.Api/Program.cs`, `.github/workflows/build.yml`, `.github/dependabot.yml`, README section "Безпека".

---

## 2. Materials needed for the sewing plan

- **Status:** TODO
- **.NET:** branch `feat/plan-materials` (2026-10-29), commit 47895cd. Check the PR number in NewErp once it is opened.
- **Why:** the old module showed a missing material only while a cut or sew operation was already consuming "what is
  there". This report says in advance what to buy for the current orders.

### Contract (must match exactly)

`GET /production/plan/materials`, same access as `GET /production/plan` (Admin and Storekeeper; Manager gets 403).

Response `PlanMaterialsDto`:

```json
{
  "materials": [
    {
      "itemId": 10, "name": "Тканина льон", "unit": "м",
      "needed": 1.6, "inStock": 1, "onOrder": 0, "shortage": 0.6,
      "uses": [ { "productionId": 1, "modelName": "Сукня", "sizeName": "M", "stage": "Крій", "quantity": 1.6 } ]
    }
  ],
  "warnings": [ "Розхідника 99 зі специфікації моделі «Шарф» немає на складі, його не враховано." ]
}
```

Rules:

- Rows: the sewing plan with `onlyToSew = true` (rows where `toSew > 0`).
- For each row (model, size): cutting needs `toCut ×` every component's cutting BOM, sewing needs `toSew ×` the sewing
  BOM's **direct items** only (component lines are produced by cutting, so they are not counted). BOM lines resolve by the
  usual rule: a line for this size overrides the all-sizes line of the same item. Exactly what the cut and sew operations
  consume (`ForCut` / `ForSewing` in .NET `ProductionService`).
- `stage` is `"Крій"` or `"Пошив"`. `uses` has one entry per (model, size, stage) with the summed quantity.
- Per stock item: `needed` = the sum; `inStock` = the item's quantity; `onOrder` = open purchase orders not yet received
  (the same number the reorder report subtracts); `shortage` = `max(0, needed − max(0, inStock) − onOrder)`.
- Order: items with `shortage > 0` first, then by name (Ukrainian culture).
- A BOM item that no longer exists in stock is left out and gives the warning text above (one per item and model).
- Load the **full** model (with the sewing BOM). In .NET, `FindModelsAsync` loads sizes only, which would silently give
  zero sewing needs.

UI: under the plan on the "План пошиву" page, a "Розхідники для плану" table: material, needed (with unit), in stock,
on order, shortage (red when above 0, "—" otherwise), and "На що" lines like `Сукня лляна M · крій 1.6`. Warnings as
alerts above the table.

### Tests to port (the specification)

- `tests/NewErp.UnitTests/Domain/MaterialNeedsTests.cs`: the size override, all components' cutting BOMs, only direct
  sewing items, nothing for zero quantities.
- `tests/NewErp.IntegrationTests/PlanMaterialsApiTests.cs`: a model with one component (fabric 0.8 m) and a sewing BOM
  (component + thread 0.1); order OrderId wants 2 M → fabric needed 1.6, in stock 1, shortage 0.6; thread 0.2, shortage
  0.2; thread first (both short, by name); Manager 403.
- `tests/NewErp.Web.Tests/SewingPlanPageTests.cs` → `Materials_ShowShortageInRed_AndWhatTheyAreFor`.

### .NET files for reference

`src/NewErp.Domain/Production/MaterialNeeds.cs`, `src/NewErp.Application/Production/PlanMaterialsService.cs`,
`src/NewErp.Application/Production/ProductionModels.cs` (PlanMaterial* records), `src/NewErp.Api/Endpoints/ProductionEndpoints.cs`,
`src/NewErp.Web/Pages/Manufacturing/SewingPlan.razor`.

---

## 3. Settings check for a copy of the real shop database (optional)

- **Status:** TODO (optional)
- **.NET:** NewErp #30 (`feat/schema-check`, 2026-10-29), tool `tools/NewErp.SchemaCheck`. No API change.
- **Why:** Phase 14. The schema part is not needed here: Hibernate `ddl-auto: validate` already stops on every table or
  column that differs. The **settings** part is worth having too: the app starts fine with a status id that does not
  exist in the real shop, and then bonuses, the sewing plan or paid vouchers silently count the wrong orders.

### What the .NET tool checks (port only the settings part)

- `Erp:LanguageId` (here `erp.language-id`) has rows in `dd_order_status`; otherwise an error listing the languages that do.
- Every id in the status lists (`.NET`: `Bonus:CountedStatusIds`, `Production:OrderSubtractStatusIds`,
  `Production:PlanStatusIds`, `ProductSort:CountedStatusIds`, `Vouchers:PaidStatusIds`; use this repo's equivalents)
  exists in `dd_order_status` for that language: a missing id is an error; each list is printed with the real names
  (`22 «5) Відправлено», 20 «Виконано (B2B)»`) so a human can confirm the meaning.
- `config_complete_status` and `config_processing_status` from `dd_setting` (`store_id = 0`) are printed with names.
  OpenCart stores them as JSON (`["5","3"]`), as an older serialized PHP array (`a:2:{i:0;s:1:"5";...}`) or as a plain
  number: take the quoted numbers, or all numbers when nothing is quoted. A warning when `config_complete_status`
  differs from the paid-voucher statuses (the site and the ERP would disagree about whether a bought voucher is valid).
- `dd_order_source` names for that language are printed.
- Read-only session, no orders or customers read, the connection string never printed.

Suggested form here: a Spring Boot `ApplicationRunner` behind a profile (e.g. `check-settings`) or a small test-scope
main, run against `newerp_shop_copy`. Tests to port: `tests/NewErp.IntegrationTests/SchemaCheckTests.cs` → the
`Settings_*` and `StatusIds_*` tests. .NET files: `tools/NewErp.SchemaCheck/SettingsCheck.cs`, `DatabaseSchema.cs`.
