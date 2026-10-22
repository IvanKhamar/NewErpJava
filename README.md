# NewErpJava

Java version of [NewErp](https://github.com/IvanKhamar/NewErp): an ERP for a sewing workshop and online clothing store
that gradually replaces the accounting part of an old OpenCart site. It works with the same MySQL database as the
.NET version, so both can run side by side.

**Status:** Phase 2 done: business rules of all modules (`newerp-domain`) and the read side of orders (`GET /orders`, card, statuses, sources, managers). Endpoints are not protected yet: login comes in Phase 3. The app starts, connects to MySQL and answers `/health`.

## Stack

Java 25 · Spring Boot 4 · Maven · Spring Data JPA (Hibernate) · MySQL 8 · springdoc-openapi · JUnit 5 · Testcontainers · ArchUnit

## Structure

```
newerp-domain          business rules in plain Java (no Spring, no database)
newerp-application     use cases and the interfaces they need
newerp-infrastructure  JPA + MySQL, external clients (Nova Poshta, SMS)
newerp-api             Spring Boot app: HTTP endpoints, security, error handling (ProblemDetails)
db/                    the same SQL as the .NET repo: OpenCart tables, reconstructed tables, erp_* tables
```

Dependencies point inward only (`api → infrastructure → application → domain`); `LayerDependencyTest` checks it.

| .NET | Java here |
|---|---|
| Solution + projects (.slnx, .csproj) | Parent `pom.xml` + Maven modules |
| `Directory.Build.props` | `<properties>` and plugins in the parent `pom.xml` |
| ASP.NET Core Minimal API | Spring Web MVC (`@RestController`) |
| EF Core, `DbContext` | Spring Data JPA, Hibernate, repositories |
| `IExceptionHandler` + ProblemDetails | `@RestControllerAdvice` + `ProblemDetail` |
| `appsettings.json` + user-secrets | `application.yml` + env vars / git-ignored `application-local.yml` |
| Scalar (`/scalar`) | Swagger UI (`/swagger-ui.html`) |
| xUnit + WebApplicationFactory | JUnit 5 + `@SpringBootTest` + Testcontainers |

## Run on Windows

You need JDK 25 (`winget install EclipseAdoptium.Temurin.25.JDK`). Maven is not needed: `mvnw.cmd` downloads it.

1. Use the same local MySQL database `newerp_dev` as the .NET version.
2. Copy `application-local.example.yml` to `application-local.yml` and put your MySQL password there
   (the file is in `.gitignore`).
3. Run:

```
.\mvnw.cmd -pl newerp-api -am spring-boot:run
```

Open http://localhost:8080/health (should say `UP`) and http://localhost:8080/swagger-ui.html.

In IntelliJ IDEA: open the folder, wait for Maven import, then run `NewErpApplication` (in `newerp-api`).
Set the working directory of the run configuration to the project folder so it finds `application-local.yml`.

## Tests

```
.\mvnw.cmd verify
```

`LayerDependencyTest` always runs. `ApplicationIntegrationTest` starts the app against a throwaway MySQL 8.4
in Docker with the tables from `db/`; without Docker it is skipped. GitHub Actions runs everything on each push.
