# Novora Platform & Data Services

Spring Boot API for Novora HRMS — auth, tenancy, HR data, and business rules.

→ Product overview: [Novora HRMS](../README.md)

---

## Quick start (local)

**Preferred — Docker + managed Postgres** (credentials in `.env`):

```bash
cd backend
cp .env.example .env   # set DB_URL, DB_USERNAME, DB_PASSWORD
docker compose up --build
# API: http://127.0.0.1:8081
```

`docker-compose` sets `SERVER_PORT=8081` and `SERVER_SERVLET_SESSION_COOKIE_SECURE=false` so Vite and mobile can use cookie sessions over plain HTTP.

**Demo without Postgres** (in-memory H2):

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
# API: http://127.0.0.1:8081
```

**Maven against Postgres** (uses `.env`):

```bash
SERVER_SERVLET_SESSION_COOKIE_SECURE=false SERVER_PORT=8081 ./mvnw spring-boot:run
```

**Tests**

```bash
./mvnw test
```

---

## Configuration

| Item | Notes |
|------|--------|
| Database | **Neon** Postgres in production (local H2 with profile `local`). See `.env.example`. |
| Hosting | **Render** runs this Spring Boot API. Do not replace it with Supabase. |
| Bootstrap admin | `APP_BOOTSTRAP_ADMIN_EMAIL` + `APP_BOOTSTRAP_ADMIN_PASSWORD` |
| Passwords | 8–72 chars with upper, lower, digit, and symbol (register + admin activate) |
| OTP | `APP_OTP_EXPOSE_CODE` defaults **false**; enable only for local demos |
| CORS | Exact origins via `APP_CORS_ADDITIONAL_ORIGIN_PATTERNS` (no wildcards with credentials) |
| Schema reset (dev) | `src/main/resources/db/manual/reset_neon_public_schema.sql` |

Env loading: `EnvFileLoader` + `.env.example`.

### Production (Render + Neon)

On the Render web service, set the same Neon vars as local `.env`:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE=5`
- `JPA_DDL_AUTO=none` (or `validate`)
- `APP_CORS_ADDITIONAL_ORIGIN_PATTERNS=https://novora-hrms.vercel.app`

Vercel proxies `/api` → this Render URL. Neon is only the database.

---

## Architecture (short)

```
Browser  →  Vercel (React)  →  Render (this Spring API)  →  Neon (Postgres)
```

Cookie sessions (`JSESSIONID`) + CSRF for the web admin; optional Firebase Bearer tokens for native.

**License:** [MIT](LICENSE)
