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
