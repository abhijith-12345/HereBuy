# HereBuy API

FastAPI backend for the Android marketplace prototype. It provides email-verified signup, phone-or-email/password login for supported accounts, profile lookup, listing CRUD, and radius-based listing discovery. Listing coordinates are seller-selected pickup pins, not live user locations.

## Run locally on Windows

From this directory, run `.\start-local.ps1`. The script creates a virtual environment, creates a local-only JWT secret in `.env`, installs dependencies, and starts the API on `http://127.0.0.1:8001`.

- Interactive API docs: `http://127.0.0.1:8001/docs`
- Health check: `http://127.0.0.1:8001/api/v1/health`
- SQLite database: `backend/data/herebuy.db`

This workstation's `FestieNgrok` Windows service forwards the reserved HTTPS domain to `127.0.0.1:8001` and starts automatically. The Android build uses that HTTPS domain in `local.properties`; don't launch a second agent for the same ngrok domain.

## Keep the backend running on Windows

The `HereBuy FastAPI Backend` Task Scheduler task starts the backend at Windows boot under the built-in `SYSTEM` account. A supervisor restarts Uvicorn if it exits. To register or refresh the task from an elevated PowerShell window, run `.\install-startup-task.ps1`. Logs are written to `backend\logs\backend.log` and per-run Uvicorn log files. The API listens on `127.0.0.1:8001` for local development and ngrok forwarding. The existing `FestieNgrok` Windows service is also configured to start at boot and forwards the app's public HTTPS URL to this port.

Check the task with `Get-ScheduledTask -TaskName 'HereBuy FastAPI Backend'`. Use `Start-ScheduledTask` or `Stop-ScheduledTask` with that task name to control it.

## API routes

- `POST /api/v1/auth/signup` (starts email verification; does not issue a token)
- `POST /api/v1/auth/signup/verify` (creates the account and issues a token after a valid code)
- `POST /api/v1/auth/signup/resend`
- `POST /api/v1/auth/login`
- `GET /api/v1/auth/me` (bearer token)
- `GET /api/v1/listings/nearby?latitude=...&longitude=...&radius_meters=...`
- `GET /api/v1/listings/{id}`
- `POST /api/v1/listings` (bearer token)
- `PATCH /api/v1/listings/{id}` (seller only)
- `DELETE /api/v1/listings/{id}` (seller only)

Signup collects a username, email, and password. The API stores passwords using Argon2id and stores only a keyed HMAC hash of each six-digit email code. Codes expire after five minutes, allow five guesses, and can only be used once; resends are cooldown-limited and capped at three. Repeated signup requests reuse the active challenge. The API sends codes through the configured SMTP account and fails closed if SMTP is unavailable. The account and bearer JWT are created only after successful code verification. Login accepts either the email or an existing E.164 phone number with the password. Authenticated routes require a signed bearer JWT.

The initial local database is SQLite so development can start without installing a database server. The SQLAlchemy models are structured so the service can move to PostgreSQL later. Use PostgreSQL with PostGIS before scaling the geospatial listing search; the current nearby endpoint calculates distances in the API process and is intended for prototype data volumes.

WebSockets are deliberately not part of this first slice. REST is enough for sign-in, profiles, listing management, and map refreshes. Add authenticated WebSockets later for chat messages or live notifications when those features are implemented.
