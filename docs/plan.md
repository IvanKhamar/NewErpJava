# Plan

Open items of this repo that are not in `docs/from-dotnet.md` or `docs/from-python.md` (features to port here).

## Move to the shared Docker database (planned 2026-11-06, not started)

All three versions should use one MySQL: the Docker one from NewErpPython (`F:\PROJECTS\NewErpPython\NewErpPython`,
`docker compose up -d mysql`, port 3307, database `newerp_dev`). Today this repo uses Windows `MySQL80`, which is behind:
86 tables against 153 in Docker on 2026-11-06.

1. Connection: change the port and password in the local, untracked settings only (see
   `application-local.example.yml`), never in committed files.
2. dbhub: create the read-only user there if it is missing and point `F:\PROJECTS\NewErpJava\dbhub.toml` at it.
3. Check that the API starts against it: `ddl-auto: validate` stops on every mapping that does not fit the Docker tables.
   The `db/reconstructed.sql` files differ until `docs/from-python.md` is ported.
4. Update the README and `CLAUDE.md`. Ask the user what happens to `newerp_dev` in `MySQL80` (it holds only invented data).

## Rule change: `dd_*` tables may be changed (2026-11-06)

`dd_` is only the prefix of the old project, so these tables may be changed when a feature needs it. A change goes into
`db/*.sql` of all three repos. The old storefront reads the same tables, so say in the PR what the change means for it.
Update the line "Never change `dd_*` tables from code" in `CLAUDE.md`.
