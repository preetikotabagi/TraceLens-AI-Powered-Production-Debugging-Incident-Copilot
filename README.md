# TraceLens

**TraceLens is an AI production debugging and incident copilot.** The finished product will ingest
logs from running services, group related errors into incidents, and help an engineer investigate the
root cause. This repository currently contains **only the foundation (milestone 1 of 5)**: a working
full-stack skeleton that later milestones build on.

> **What exists today:** a React dashboard, a Spring Boot API with a health check, a PostgreSQL database
> managed by migrations, and a one-command local setup with Docker Compose.
>
> **What does not exist yet:** log ingestion, incident detection, AI investigation and production
> hardening. The dashboard pages for them show honest empty states, not fake data.

## Contents

1. [Architecture](#architecture)
2. [Technology versions](#technology-versions)
3. [How the pieces fit together (beginner-friendly)](#how-the-pieces-fit-together-beginner-friendly)
4. [Prerequisites](#prerequisites)
5. [Quick start with Docker (recommended)](#quick-start-with-docker-recommended)
6. [Running without Docker (for development)](#running-without-docker-for-development)
7. [Environment variables](#environment-variables)
8. [Verify that it is running](#verify-that-it-is-running)
9. [API reference](#api-reference)
10. [Running the tests](#running-the-tests)
11. [Project structure](#project-structure)
12. [Troubleshooting](#troubleshooting)

## Architecture

```
 Browser
    |  http://localhost:3000
    v
+--------------------+      REST / JSON       +----------------------+      JDBC      +--------------+
| frontend           | ---------------------> | backend              | ------------> | db           |
| React + Vite       |  GET /api/v1/health    | Spring Boot (Java 21)|               | PostgreSQL 16|
| served by nginx    | <--------------------- | controller           | <------------ |              |
+--------------------+      http://localhost  |   -> service         |               +--------------+
                                :8080         |   -> repository      |                     ^
                                              +----------------------+                     |
                                                        |  Flyway runs SQL migrations at startup
                                                        +--------------------------------------+
```

Backend layers (package `com.tracelens`):

| Package      | Responsibility                                                                    |
|--------------|-----------------------------------------------------------------------------------|
| `controller` | HTTP only: map URLs to calls, return status codes. No business logic.            |
| `service`    | Business logic (health report, demo error generator).                             |
| `repository` | The only code that talks to the database.                                         |
| `dto`        | Request and response shapes that cross the API boundary.                          |
| `exception`  | One central handler that turns every error into the same JSON shape.              |
| `config`     | Typed settings, CORS, and the production safety guard for demo mode.              |
| `web`        | Request-id filter: every request gets an id that appears in its log lines.        |

### Important decisions

- **Layered backend.** Keeps HTTP, logic and SQL separate so each can change and be tested alone.
- **Flyway migrations.** The schema is defined by versioned SQL files, so every machine gets the same database.
- **One error format.** Every failure returns `{ timestamp, status, code, message, path, details, requestId }`.
  Stack traces and connection details never reach the client; they go to the server log only.
- **Health endpoint returns 503 when the database is down** (with a JSON body), so Docker, load
  balancers and the dashboard can all react to it without parsing text.
- **Demo errors are opt-in and cannot reach production.** The demo controller and service only exist when
  `TRACELENS_DEMO_ENABLED=true` (the `dev` profile turns it on). If demo mode is combined with the `prod`
  profile, the application refuses to start.
- **Structured logs.** With `LOGGING_STRUCTURED_FORMAT_CONSOLE=logstash` (default in Docker) the backend
  prints one JSON object per line including the `requestId`, which a later milestone can ingest.
- **Environment-driven configuration.** No credentials in source code; `.env.example` files hold placeholders only.
- **Frontend API address is an environment variable** (`VITE_API_BASE_URL`), read at build time.

## Technology versions

| Component         | Version                                   | Notes                                                       |
|-------------------|-------------------------------------------|-------------------------------------------------------------|
| Java              | 21 (LTS)                                  | Set in `backend/pom.xml`                                    |
| Spring Boot       | 3.5.7                                     | Pinned in `backend/pom.xml`; manages Spring 6.2, Flyway 11, JUnit 5, Testcontainers |
| Build tool        | Maven 3.9+ (Docker image uses 3.9)        |                                                             |
| PostgreSQL        | 16 (`postgres:16-alpine`)                 |                                                             |
| Flyway            | managed by Spring Boot                    | `flyway-core` + `flyway-database-postgresql`                |
| Node.js           | 22 (20.19+ works)                         |                                                             |
| React             | 19.3.1                                    |                                                             |
| React Router      | 7.18.4                                    |                                                             |
| Vite              | 8.3.5                                     | Plugin: `@vitejs/plugin-react` 6.1.2                        |
| Vitest            | 5.0.5                                     | With Testing Library and jsdom                              |
| ESLint            | 10.13.0                                   | `eslint-plugin-react-hooks` 7.1.1                           |
| nginx (frontend)  | 1.27-alpine                               | Serves the built dashboard                                  |

## How the pieces fit together (beginner-friendly)

- **React** builds the screens you see in the browser (sidebar, cards, status light). It runs in your
  browser and asks the backend for data. **Vite** is the tool that bundles it and gives fast reloads in development.
- **Spring Boot** is the Java server. It receives requests, applies the rules and talks to the database.
  The browser never touches the database directly; it only talks to Spring Boot.
- **PostgreSQL** is the database: long-term storage for the data TraceLens will keep (logs, incidents, ...).
- **REST APIs** are how the two halves talk: the frontend sends an HTTP request such as `GET /api/v1/health`
  and receives JSON back. The URL says what you want, the HTTP method says what to do, the status code says how it went.
- **Docker** packages each part with everything it needs to run, and **Docker Compose** starts all three together,
  in the right order, with one command. That makes "it works on my machine" true on every machine.
- **Database migrations (Flyway)** are numbered SQL files (`V1__...sql`, `V2__...sql`) that build the
  database structure step by step. On startup Flyway applies any that are missing and records which ones ran,
  so the database layout is versioned and repeatable just like your code.

## Prerequisites

For the Docker route you only need:

- **Docker Desktop** (Windows 10/11 with WSL 2 enabled, or macOS) or Docker Engine + Compose on Linux
- **Git** (optional, to clone the repository)

For running the parts directly (without Docker) you also need:

- **JDK 21** and **Maven 3.9+** (check: `java -version`, `mvn -version`)
- **Node.js 20.19+** (22 recommended) and npm (check: `node -v`, `npm -v`)
- A **PostgreSQL 16** server, or just start the `db` service from Compose (see below)

## Quick start with Docker (recommended)

Run these in **PowerShell** on Windows, from the repository root:

```powershell
# 1. Create your local settings file and (optionally) edit the password inside it
Copy-Item .env.example .env

# 2. Build and start the database, backend and frontend
docker compose up --build

# Or run in the background:
docker compose up --build -d
```

Compose starts the services in order and waits for each to be healthy: **db -> backend -> frontend**.
The first build downloads dependencies and can take several minutes.

| What                   | URL                                        |
|------------------------|--------------------------------------------|
| Dashboard              | <http://localhost:3000>                    |
| Backend health check   | <http://localhost:8080/api/v1/health>      |
| PostgreSQL (localhost) | `localhost:5432` (user and db from `.env`) |

Useful commands:

```powershell
docker compose ps                    # service status and health
docker compose logs -f backend       # follow backend logs (JSON lines)
docker compose down                  # stop everything, keep database data
docker compose down -v               # stop everything AND delete database data
```

> Changing `VITE_API_BASE_URL` requires rebuilding the frontend: `docker compose up --build`.

## Running without Docker (for development)

**1. Database** (only the database from Compose):

```powershell
Copy-Item .env.example .env
docker compose up -d db
```

**2. Backend** (new terminal). Spring Boot reads environment variables, not `.env` files, so set them in the shell:

```powershell
cd backend
$env:DB_PASSWORD = "<the POSTGRES_PASSWORD from your .env>"
$env:SPRING_PROFILES_ACTIVE = "dev"
mvn spring-boot:run
```

The defaults in `application.yml` (`localhost:5432`, database and user `tracelens`) match the Compose database,
so `DB_PASSWORD` is the only value you must provide.

**3. Frontend** (new terminal):

```powershell
cd frontend
Copy-Item .env.example .env
npm install
npm run dev
```

Open <http://localhost:5173>.

## Environment variables

Never commit a real `.env` file. Only the `.env.example` files are committed.

### Root `.env` (read by Docker Compose)

| Variable                            | Default (local only)                            | Purpose                                              |
|-------------------------------------|-------------------------------------------------|------------------------------------------------------|
| `POSTGRES_DB`                       | `tracelens`                                     | Database name                                        |
| `POSTGRES_USER`                     | `tracelens`                                     | Database user                                        |
| `POSTGRES_PASSWORD`                 | `tracelens_dev_password`                        | Database password. **Change it.**                    |
| `DB_PORT`                           | `5432`                                          | Host port for PostgreSQL (bound to localhost only)   |
| `BACKEND_PORT`                      | `8080`                                          | Host port for the API                                |
| `FRONTEND_PORT`                     | `3000`                                          | Host port for the dashboard                          |
| `SPRING_PROFILES_ACTIVE`            | `dev`                                           | `dev` enables demo endpoints; `prod` forces them off |
| `TRACELENS_CORS_ALLOWED_ORIGINS`    | `http://localhost:3000,http://localhost:5173`   | Browser origins allowed to call the API              |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | `logstash`                                      | JSON log format (`logstash` or `ecs`)                |
| `VITE_API_BASE_URL`                 | `http://localhost:8080`                         | Backend address as seen by the browser (build time)  |

### Backend (`backend/.env.example`, read from the process environment)

| Variable                         | Default                                      | Purpose                                         |
|----------------------------------|----------------------------------------------|-------------------------------------------------|
| `DB_URL`                         | `jdbc:postgresql://localhost:5432/tracelens` | JDBC connection string                          |
| `DB_USERNAME`                    | `tracelens`                                  | Database user                                   |
| `DB_PASSWORD`                    | `tracelens_dev_password`                     | Database password                               |
| `SERVER_PORT`                    | `8080`                                       | HTTP port                                       |
| `TRACELENS_CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000`| Allowed browser origins                         |
| `SPRING_PROFILES_ACTIVE`         | _(none)_                                     | `dev` or `prod`                                 |
| `TRACELENS_DEMO_ENABLED`         | `false`                                      | Demo error endpoints. **Must be false in production.** |

### Frontend (`frontend/.env.example`)

| Variable            | Default                 | Purpose                                                                 |
|---------------------|-------------------------|-------------------------------------------------------------------------|
| `VITE_API_BASE_URL` | `http://localhost:8080` | Backend URL. Public: everything `VITE_*` is visible in the browser bundle. |

## Verify that it is running

1. `docker compose ps` shows `db`, `backend` and `frontend` as **healthy**.
2. Open <http://localhost:8080/api/v1/health>. Expected (values vary):

   ```json
   {
     "status": "UP",
     "application": { "name": "TraceLens", "version": "0.1.0" },
     "database": { "status": "UP", "latencyMs": 5, "migrationVersion": "1" },
     "timestamp": "2026-10-24T06:30:00Z"
   }
   ```

   From PowerShell: `Invoke-RestMethod http://localhost:8080/api/v1/health`
3. Open <http://localhost:3000>. The top-right indicator should read **Backend connected** (green).
4. Stop the database (`docker compose stop db`). Within about 15 seconds the indicator changes to
   **Database unavailable** and the health URL returns HTTP 503. Start it again with `docker compose start db`.
5. In the dashboard go to **Settings -> Demo scenarios** and run each scenario. You should see HTTP 503, 400
   and 200 results with a request id; the same id appears in `docker compose logs backend`.

## API reference

| Method | Path                       | Description                                                                 |
|--------|----------------------------|-----------------------------------------------------------------------------|
| GET    | `/api/v1/health`           | Application and database status. 200 when healthy, 503 when the database is unreachable. |
| POST   | `/api/v1/demo/simulate`    | **Development only.** Body `{"scenario": "DATABASE_FAILURE" \| "INVALID_REQUEST" \| "SLOW_REQUEST", "delayMs": 3000}`. Returns 404 when demo mode is off. |

Error response (all endpoints):

```json
{
  "timestamp": "2026-10-24T06:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Request validation failed.",
  "path": "/api/v1/demo/simulate",
  "details": [{ "field": "scenario", "message": "scenario is required" }],
  "requestId": "0d9f6c9e-..."
}
```

## Running the tests

```powershell
# Backend: unit + web-layer tests. The PostgreSQL integration test runs only if Docker is available.
cd backend
mvn verify

# Frontend
cd frontend
npm test            # unit and component tests
npm run lint        # static analysis
npm run build       # production build
```

## Project structure

```
tracelens/
|-- docker-compose.yml        # db -> backend -> frontend, with health checks
|-- .env.example              # Compose settings (placeholders)
|-- backend/                  # Spring Boot (Maven)
|   |-- pom.xml
|   |-- Dockerfile
|   `-- src/
|       |-- main/java/com/tracelens/   # controller, service, repository, dto, exception, config, web
|       |-- main/resources/            # application*.yml, db/migration/V1__create_app_metadata.sql
|       `-- test/java/com/tracelens/   # unit, web-layer and Testcontainers tests
`-- frontend/                 # React + Vite
    |-- Dockerfile, nginx.conf
    `-- src/                  # api/, components/, context/, hooks/, pages/, styles/, test/
```

## Troubleshooting

- **Port already in use** (5432, 8080 or 3000): change `DB_PORT`, `BACKEND_PORT` or `FRONTEND_PORT` in `.env`.
- **Dashboard says "Backend unreachable"**: check `docker compose ps` and `docker compose logs backend`; if you
  changed the backend port, also update `VITE_API_BASE_URL` and rebuild with `docker compose up --build`.
- **Browser shows a CORS error**: add the dashboard's origin to `TRACELENS_CORS_ALLOWED_ORIGINS`.
- **Backend cannot connect to the database**: the password in `.env` changed after the database volume was
  created. Reset with `docker compose down -v` (this deletes local data) and start again.
- **Demo buttons answer "disabled"**: the backend is not running with the `dev` profile / `TRACELENS_DEMO_ENABLED=true`.

## Roadmap

This is milestone 1 of 5. Planned next: log ingestion, incident detection, AI-assisted investigation, and
production hardening. None of these are implemented yet.
