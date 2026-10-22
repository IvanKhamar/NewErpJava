# NewErpJava

Java rewrite of NewErp (.NET 10, github.com/IvanKhamar/NewErp). Same business, same MySQL database `newerp_dev`,
same HTTP API contract, so both versions can run side by side and the existing Blazor UI can talk to this API.
The plan with phases lives in the project's plan doc; the .NET code is the reference for behaviour.

## Stack

JDK 25 (Temurin), Spring Boot 4, Maven multi-module with the wrapper (`mvnw`), Spring Data JPA + Hibernate,
MySQL Connector/J, springdoc-openapi, JUnit 5 + AssertJ + Mockito, Testcontainers MySQL, ArchUnit.

## Structure and code rules

- Modules = layers: `newerp-domain ← newerp-application ← newerp-infrastructure ← newerp-api`.
  Maven dependencies and `LayerDependencyTest` (ArchUnit) both enforce that dependencies point inward only.
- Domain is plain Java: no Spring, no JPA annotations. JPA entities and mapping live in infrastructure.
- Packages: `ua.newerp.<layer>.<module>` (e.g. `ua.newerp.domain.stock`). Business modules
  (orders, stock, production, delivery…) are packages inside each layer, not separate Maven modules.
- Keep the API contract identical to the .NET version: same paths, JSON field names, status codes and
  ProblemDetails titles (`ErrorHandler`). When in doubt, read the matching .NET endpoint.
- Compiler warnings are errors (`-Werror` in the parent pom).
- Dead code of the old OpenCart ERP (OpenBay, foreign payment gateways) is not ported.

## Database

- Database-first: `spring.jpa.hibernate.ddl-auto=validate`. Never change `dd_*` tables from code.
- `db/schema.sql`, `db/reconstructed.sql`, `db/erp.sql` are copies from the .NET repo; keep them in sync with it.
  Integration tests load them into a throwaway MySQL (Testcontainers).
- Do not invent tables or columns. New own tables only with the `erp_` prefix, and add them to the .NET repo too.
- Passwords only in env vars (`NEWERP_DB_URL`, `NEWERP_DB_USER`, `NEWERP_DB_PASSWORD`) or the git-ignored
  `application-local.yml`, never in `application.yml`.
- Only invented data; no real customer data.

## Commands

```
./mvnw verify                                  # build + all tests (integration tests skip without Docker)
./mvnw -pl newerp-api -am spring-boot:run      # run the app; /health, /swagger-ui.html
```

## Working with the user

- The user is on Windows, knows C#/.NET and is learning Java. The goal is to get the system done fast:
  write the code yourself, and explain each new Java/Spring concept in 1–2 sentences (map it to .NET) in the PR.
- Replies short and to the point. One recommendation, not several options.
- Work in small PRs per phase step. Run `./mvnw verify` once at the end of a task, trim output.
