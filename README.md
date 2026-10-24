# NewErpJava

Java version of [NewErp](https://github.com/IvanKhamar/NewErp): an ERP for a sewing workshop and online clothing store
that gradually replaces the accounting part of an old OpenCart site. It works with the same MySQL database as the
.NET version, so both can run side by side.

**Status:** the back end of NewErp up to the .NET port base of Oct 16 (orders, login and users, stock, production, bonuses, SMS, Nova Poshta), plus the Checkbox cash register, payments (PrivatBank statement, LiqPay), creating orders by hand (with the customer risk check and order composition changes), sewn stock on the storefront, pre-orders, the sewing plan, the cutting plan (by size shares and the model minimum, with a consumables check), reseller partners with their XML feeds, the product order by sales, the checkout cross-sell panel contents (categories and products with discounts), customers, product returns, warehouses with Excel import and export, low-stock alerts on the storekeeper home page, consumables at several warehouses (transfers, use and receipt per warehouse, deliveries into a warehouse, stocktaking per warehouse, stock by warehouse), sewn pieces at several warehouses (sewn into and shipped from a warehouse, transfers, stocktaking per warehouse), stock reports (stock value by category, consumption with days left and a reorder for the next weeks at that pace, production output by size), consumables and production stocktaking (Excel sheet or typed in; cut, sewn and component stock per model), sales reports with the home page summary, a token-protected revenue feed for an external dashboard, marketing (birthday greetings with coupons, discount coupons with their usage in orders, contact export), and real SMS through SMSClub when a token is set. The existing Blazor UI runs on top of it, and a Java UI in Vaadin is being built under `/ui/` (so far: login, home page with the sales summary, sales reports, warehouses and pick-up points with Excel import and export, reseller partners with their feed mapping, the product order by sales with pins and snapshots, size pre-orders, discount coupons, birthday greetings with coupons and the contact export, users with roles and temporary passwords, managers' bonuses, materials in stock with supplier orders, the reorder list, suppliers, stock reports and stock by warehouse, production (models with stock operations and sewn pieces per warehouse, BOMs and shop links, the sewing plan and log), the cash register (cashiers, shifts, receipts, Z reports), bank payments with order matching, order list, new order, returns, customers, delivery, and the order card with its dialogs: new waybill, order composition, product return, LiqPay link; see Web UI).

## Stack

Java 25 · Spring Boot 4 · Maven · Spring Data JPA (Hibernate) · MySQL 8 · Vaadin 25 · springdoc-openapi · JUnit 5 · Testcontainers · ArchUnit

## Structure

```
newerp-domain          business rules in plain Java (no Spring, no database)
newerp-application     use cases and the interfaces they need
newerp-infrastructure  JPA + MySQL, external clients (Nova Poshta, SMS)
newerp-web             Vaadin UI: Java views that call the application services directly
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

## Web UI

**Vaadin UI (Java).** Open http://localhost:8080/ (it goes to `/ui/`) and log in with a NewErp login. The pages are Java
classes in `newerp-web` that run on the server; no Node.js is needed, the build packs Vaadin's ready browser bundle into
the jar. Logging in here or through the API gives the same session, so both UIs work side by side.

**Blazor UI (.NET).** The Java API keeps the same routes and JSON as the .NET one, so the existing Blazor UI (`NewErp.Web`) works with it.
Publish it once from the .NET repository and point the Java app to its `wwwroot`:

```
dotnet publish src\NewErp.Web -c Release -o F:\PROJECTS\newerp-web
```

```yaml
# application-local.yml
newerp:
  web-root: F:/PROJECTS/newerp-web/wwwroot
```

Then open http://localhost:8080/app/. Without `newerp.web-root` the app runs as an API only.

## Deploy

One jar with Tomcat inside: `.\mvnw.cmd -DskipTests package`, then `start-newerp.cmd`, or the `Dockerfile`.
Settings, the `prod` profile and running as a Windows service: [docs/deploy.md](docs/deploy.md).

## Tests

```
.\mvnw.cmd verify
```

`LayerDependencyTest` always runs. `ApplicationIntegrationTest` starts the app against a throwaway MySQL 8.4
in Docker with the tables from `db/`; without Docker it is skipped. GitHub Actions runs everything on each push.
