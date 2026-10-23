# Deploying NewErpJava

The app is one jar, `newerp-api/target/newerp.jar`, with Tomcat inside. It needs Java 25 and the MySQL database
`newerp_dev` (the same one the old site and the .NET version use). Nothing else.

## Build

```
.\mvnw.cmd -DskipTests package
```

`-DskipTests` because the tests need Docker; CI runs them on every push. Each green build on `main` also keeps the jar
as a download in GitHub Actions (the run's *Artifacts*, `newerp-jar`).

## Settings

Everything in `application.yml` can be overridden with environment variables, so secrets never go into git.
Spring turns a setting name into a variable name by removing dashes, replacing dots with `_` and upper-casing.

| Setting | Environment variable |
| --- | --- |
| database URL | `NEWERP_DB_URL` (for example `jdbc:mysql://localhost:3306/newerp_dev`) |
| database user / password | `NEWERP_DB_USER`, `NEWERP_DB_PASSWORD` |
| production profile | `SPRING_PROFILES_ACTIVE=prod` |
| web UI folder (published Blazor `wwwroot`) | `NEWERP_WEBROOT` |
| Nova Poshta tracking key | `NOVAPOSHTA_APIKEY` |
| Nova Poshta senders | `NOVAPOSHTA_SENDERS_0_ID`, `NOVAPOSHTA_SENDERS_0_APIKEY`, `NOVAPOSHTA_SENDERS_0_COUNTERPARTYREF`, `NOVAPOSHTA_SENDERS_0_CONTACTREF`, `NOVAPOSHTA_SENDERS_0_PHONE`, `NOVAPOSHTA_SENDERS_0_ISDEFAULT` |
| Nova Poshta drop-off points | `NOVAPOSHTA_DROPOFFPOINTS_0_ID`, `..._CITYREF`, `..._ADDRESSREF`, `..._ISDEFAULT` |

Instead of variables, the same settings can go into `application-local.yml` next to the jar (or in the folder the
app is started from); see `application-local.example.yml`. That file is in `.gitignore`.

The `prod` profile (`application-prod.yml`):
- sends the login cookie only over HTTPS,
- trusts the `X-Forwarded-*` headers of a reverse proxy,
- turns off Swagger,
- writes the log to `logs/newerp.log` as well as the console.

So in production put the app behind a reverse proxy that does HTTPS (IIS with URL Rewrite + ARR, nginx or Caddy) and
forwards to `http://localhost:8080`.

## Windows

1. Install Temurin 25 JRE or JDK (`winget install EclipseAdoptium.Temurin.25.JDK`).
2. Copy `newerp.jar`, `start-newerp.cmd` and your `application-local.yml` into one folder
   (`start-newerp.cmd` expects the jar in `newerp-api\target\`; edit the path in it if you keep the jar elsewhere).
3. Run `start-newerp.cmd` and open http://localhost:8080/health. It should say `UP`.

To run it as a Windows service that starts with the computer, wrap the same command with
[WinSW](https://github.com/winsw/winsw) or NSSM: the service runs `java -jar newerp.jar` in that folder
with `SPRING_PROFILES_ACTIVE=prod`.

## Docker

```
docker build -t newerp .
docker run -d --name newerp -p 8080:8080 \
  -e NEWERP_DB_URL=jdbc:mysql://host.docker.internal:3306/newerp_dev \
  -e NEWERP_DB_USER=newerp -e NEWERP_DB_PASSWORD=... \
  newerp
```

The image already has `SPRING_PROFILES_ACTIVE=prod`. To serve the web UI, mount the published Blazor folder and
point `NEWERP_WEBROOT` at it: `-v F:/PROJECTS/newerp-web/wwwroot:/web -e NEWERP_WEBROOT=/web`.

## After starting

- `GET /health` checks the app and the database.
- Log in with a user that has a NewErp login (`erp_user_login`); the old site's passwords do not work here.
- Without a Nova Poshta key the delivery screens work on the stub: nothing reaches Nova Poshta.
- `dd_*` tables are only read and changed through the same columns the old site uses; `ddl-auto: validate` makes the app
  refuse to start if the database does not match, instead of changing the schema.
