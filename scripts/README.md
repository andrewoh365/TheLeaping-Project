# Dev Scripts

## `dev.sh` — reset the database and run the app locally

One command that gives you a clean, identical database and starts the backend and frontend together.

```bash
scripts/dev.sh
```

When it finishes you'll see `App ready at http://localhost:4200`. Press **Ctrl-C** to stop both servers.

### Options

| Command | What it does |
|---|---|
| `scripts/dev.sh` | Wipe and reseed `leaping_db`, then start backend + frontend |
| `scripts/dev.sh --no-reset` | Start backend + frontend, keeping the data you already have |
| `scripts/dev.sh --reset-only` | Wipe and reseed `leaping_db`, then exit (nothing left running) |
| `scripts/dev.sh --no-seed-users` | Skip the demo customers below (Flyway seed data only) |
| `scripts/dev.sh --help` | Show usage |

Options can be combined, e.g. `scripts/dev.sh --reset-only --no-seed-users`.

### What it does

1. **Frees ports 8080 and 4200.** Anything *listening* on those ports (e.g. a backend or `ng serve` you left running) is stopped. Browser tabs connected to the app are not affected.
2. **Resets the database.** Closes any open connections to `leaping_db` (pgAdmin, your IDE), drops it, and recreates it empty.
3. **Starts the backend.** Flyway rebuilds the schema and reference data from `backend/portfolio-app/src/main/resources/db/migration/`. The `dev` Spring profile is enabled so `DataSeeder` adds the demo customers.
4. **Starts the frontend.** Runs `npm install` automatically the first time (if `frontend/node_modules` is missing), then `ng serve`.
5. **Watches both.** If either server crashes, the other is stopped too and the script exits.

Because the data comes entirely from the migrations and `DataSeeder`, every reset produces exactly the same data on every machine.

### Demo logins

Created on every reset (unless `--no-seed-users`):

| Email | Password | Starting cash |
|---|---|---|
| `john.trader@example.com` | `password123` | $10,000 |
| `sarah.investor@example.com` | `password456` | $10,000 |
| `demo@example.com` | `password789` | $5,000 |

### Logs

Server output goes to files instead of the terminal:

- `logs/backend.log`
- `logs/frontend.log`

These are overwritten on each run and are gitignored. Follow one live with `tail -f logs/backend.log`.

---

## First-time setup

You only need to do this once per machine.

### 1. Install the prerequisites

| Tool | Version | Check with |
|---|---|---|
| Java (JDK) | 21 | `java -version` |
| Node.js + npm | 18 or newer | `node -v` |
| PostgreSQL | 14 or newer (17 recommended), running on `localhost:5432` | `pg_isready` |
| `psql`, `pg_isready` | on your `PATH` | `which psql` |
| `lsof` | preinstalled on macOS; `sudo apt install lsof` on Linux | `which lsof` |

Maven does **not** need to be installed — the script uses the bundled `./mvnw`.

On macOS with Homebrew:

```bash
brew install postgresql@17 node openjdk@21
brew services start postgresql@17
```

> **Postgres.app / EDB installer users:** `psql` may not be on your `PATH`. Add its `bin` folder, e.g.
> `export PATH="/Applications/Postgres.app/Contents/Versions/latest/bin:$PATH"` or
> `export PATH="/Library/PostgreSQL/17/bin:$PATH"` in your `~/.zshrc`.

### 2. Make sure you have a database user that can create databases

The script drops and recreates `leaping_db`, so the user it connects as needs to be a superuser or have `CREATEDB`. It connects to the built-in `postgres` database to do this.

- **Standard install (EDB / Postgres.app / Linux):** you already have a `postgres` superuser — nothing to do.
- **Homebrew install:** the only user is your macOS username (no `postgres` role). Either use your username in step 3, or create a `postgres` role:

  ```sql
  CREATE ROLE postgres WITH LOGIN SUPERUSER PASSWORD 'choose_a_password';
  ```

You do **not** need to create `leaping_db` yourself — the script does it.

### 3. Create `application-local.yaml`

```bash
cp backend/portfolio-app/src/main/resources/application-local.yaml.example \
   backend/portfolio-app/src/main/resources/application-local.yaml
```

Edit it:

```yaml
spring:
  datasource:
    username: postgres          # or your macOS username on Homebrew
    password: your_db_password  # use "" if your local Postgres needs no password

jwt:
  secret: "paste-a-long-random-string-here"
```

Generate a JWT secret with `openssl rand -hex 64`. The file is gitignored — never commit it.

The script reads `username` and `password` from this file. You can override them with the `DB_USERNAME` / `DB_PASSWORD` environment variables, and override the database location with `DB_HOST` / `DB_PORT`.

### 4. Run it

```bash
scripts/dev.sh
```

The first run is slower (Maven downloads dependencies, `npm install` runs).

---

## Platform support

| Platform | Status |
|---|---|
| macOS | ✅ Supported (works with the built-in bash 3.2) |
| Linux | ✅ Supported (needs `lsof`) |
| Windows | ⚠️ Use **WSL2** and run the script from inside it. Git Bash and PowerShell are not supported. |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Missing .../application-local.yaml` | Do [step 3](#3-create-application-localyaml). |
| `Postgres is not accepting connections` | Start Postgres: `brew services start postgresql@17` (macOS) or `sudo systemctl start postgresql` (Linux). |
| `psql not found` | Install Postgres client tools or add them to your `PATH` (see step 1). |
| `role "postgres" does not exist` | Homebrew install — see [step 2](#2-make-sure-you-have-a-database-user-that-can-create-databases). |
| `password authentication failed` | The `password` in `application-local.yaml` doesn't match your Postgres user. |
| `permission denied to create database` | Your DB user lacks `CREATEDB` — run `ALTER ROLE your_user CREATEDB;` as a superuser. |
| `Backend exited during startup` | The last 40 lines of `logs/backend.log` are printed — usually a bad DB password, missing JWT secret, or wrong Java version. |
| `Could not free port 8080` | Something you don't own is on the port. Find it with `lsof -i :8080`. |
| `permission denied: scripts/dev.sh` | Run `chmod +x scripts/dev.sh`. |
| `bad interpreter: /usr/bin/env: bash^M` | The file got Windows line endings. Run `git rm --cached scripts/dev.sh && git checkout scripts/dev.sh` (`.gitattributes` keeps `.sh` files as LF). |
