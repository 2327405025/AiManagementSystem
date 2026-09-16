# Technical Guide

[中文](TECHNICAL.zh-CN.md) · [Back to English README](../README.en.md)

## 1. Runtime
- Java 21, Spring Boot 3 and Maven 3.9+
- React, TypeScript, Vite and Node.js 24+
- PostgreSQL 17: task source of truth
- Redis 7: optional shared L2 cache
- ChromaDB: rebuildable vector index
- Docker Desktop and Docker Compose

## 2. Module Layout
```text
backend/
  controller/    HTTP parameters and responses
  service/       business rules and transaction boundaries
  repository/    JPA data access
  domain/        entities and enums
  cache/         Caffeine + Redis two-level cache
  ai/            OpenAI-compatible AI and rule fallback
  vector/        Chroma indexing, search and reconciliation
  health/        optional dependency health
frontend/
  src/api.ts     HTTP client
  src/App.tsx    queries, mutations and UI
```

## 3. Configuration
Copy `.env.example` to `.env`. Keep real secrets only in `.env`:

```env
OPENAI_API_KEY=your_DeepSeek_API_key
OPENAI_BASE_URL=https://api.deepseek.com/v1
OPENAI_MODEL=deepseek-flash
```

Use the exact model shown in your account console if it differs. Other important variables:
- `DB_POOL_MAX`: database connection limit; default 16.
- `CACHE_LOCAL_MAX_SIZE`: Caffeine entry limit; default 10000.
- `CACHE_LOCAL_TTL` / `CACHE_REDIS_TTL`: cache lifetimes.
- `AI_POOL_*` / `INDEX_POOL_*`: bounded executor and queue sizes.
- `CHROMA_ENABLED` / `CHROMA_URL`: vector index switch and address.

### Template vs. Private Configuration
- `.env.example` is the public template. It contains an empty key, non-secret defaults and variable names.
- `.env` is the private file on your machine and contains the real DeepSeek key. `.gitignore` ignores `.env` and other `.env.*` files while explicitly allowing `.env.example`.
- Docker Compose reads the root `.env` at startup, so no secret is written into source code or the image.
- Never put the key in a `VITE_*` variable. Vite bundles those values into browser assets where every visitor can read them.

Check before committing:

```powershell
git check-ignore .env  # should print .env
git ls-files .env      # should print nothing
git status --short     # must not list .env
```

If a key was ever committed, deleting the file later is insufficient because Git history and existing clones may retain it. Revoke and regenerate the key in the DeepSeek console immediately.

## 4. Data and Consistency
- Flyway executes `V1`–`V3` to create tasks, dependencies, optimistic locking, indexes and idempotency records.
- `tasks.version` uses JPA `@Version` to detect concurrent overwrites.
- `task_dependencies` has a composite primary key and two foreign keys; the service rejects self and indirect cycles.
- `Idempotency-Key` and request hashes are durable; database uniqueness resolves concurrent races.
- Cache eviction and index events run only after a successful write commit.

## 5. Cache and Search
- ID reads use Cache-Aside: Caffeine L1 → Redis L2 → PostgreSQL.
- Caffeine atomically coalesces concurrent misses for the same key.
- Redis TTL has random jitter; Redis failures fall through to PostgreSQL.
- The regular UI uses offset pages; deep pages use a `(created_at, id)` keyset cursor.
- Semantic search falls back to database keyword filtering when Chroma is unavailable.

## 6. AI Requests
The backend calls `${OPENAI_BASE_URL}/chat/completions` with a Bearer key and JSON Output. AI only produces suggestions and never writes directly to the database. Missing keys, timeouts, rate limits or open circuits trigger deterministic local rules. The response `source` is either `llm` or `rules`.

## 7. Local and Docker Commands
```powershell
# Complete stack
Copy-Item .env.example .env
docker compose up --build -d
docker compose ps
docker compose logs -f backend

# Stop and retain data
docker compose down
```

For local development:

```powershell
cd backend
mvn spring-boot:run

cd ..\frontend
npm install
npm run dev
```

## 8. Verification and Observability
```powershell
cd backend
mvn test

cd ..\frontend
npm run lint
npm run build
```

- `/livez`: process liveness only.
- `/readyz`: database required; Redis failures report `DEGRADED` with HTTP 200.
- `/actuator/prometheus`: JVM, HTTP, HikariCP, Caffeine and Resilience4j metrics.

## 9. Troubleshooting
- AI returns `rules`: check the key, model, account balance and `docker compose logs -f backend`.
- `/readyz` returns 503: inspect the PostgreSQL container and migration logs.
- Semantic search falls back: inspect Chroma health; task CRUD remains available.
- First startup is slow: Docker downloads images and Chroma prepares its embedding model.
