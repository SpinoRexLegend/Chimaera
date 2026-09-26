# CHIMAERA

Different minds. One creation.

This repository is a lean, runnable starter for the core CHIMAERA demo path described in the project abstract:

1. Create a Quest.
2. Extract editable capability requirements.
3. Rank eligible collaborators with explainable factors.
4. Draft a proposal.
5. Require explicit human approval before sending it.

## Architecture

- `frontend/`: React 19 + Vite user interface.
- `backend/`: Spring Boot REST API and authoritative business rules.
- `ai-service/`: FastAPI service with deterministic analysis and a transparent weighted ranker.
- MySQL 8.4 is the system of record. Flyway owns its versioned schema and
  Hibernate validates entity compatibility. The backend retains an H2 local/test
  profile for fast development and rollback.

The Python service is intentionally deterministic at this stage. It exercises the service boundary and fallback behavior without requiring an API key or pretending that synthetic data is a trained production model.

## Run locally

Prerequisites: Docker Desktop (simplest route), or Java 17+, Maven, Node.js 20+, and Python 3.11+.

```bash
docker compose up --build
```

Open `http://localhost:5173`. API documentation is available at `http://localhost:8000/docs` for the AI service.

For local development without Docker:

```bash
# terminal 1
cd ai-service
python -m venv .venv
.venv/Scripts/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000

# terminal 2
cd backend
mvn spring-boot:run

# terminal 3
cd frontend
npm install
npm run dev
```

Set `SPRING_DATASOURCE_*`, `AI_SERVICE_URL`, or `VITE_API_URL` to override the defaults.

## Supabase authentication

Copy `.env.example` to `.env`, add the Supabase project URL and publishable
key, and keep `AUTH_ENABLED=true`. Add the active local URL (currently
`http://localhost:5175`) and the production site URL to the Supabase Auth
redirect allow list.

The browser uses only the publishable key. Spring Boot validates access tokens
against the Supabase JWKS endpoint and derives the application identity from the
verified JWT subject. Never place a Supabase secret or service-role key in a
`VITE_*` variable.

## Focused verification

```bash
cd ai-service
python -m unittest discover -s tests -v

cd ../backend
mvn test

cd ../frontend
npm install
npm run build
```

## Production-like prototype

`compose.production.yml` builds an optimized frontend served by Nginx, keeps
MySQL and the AI service off host ports, enables authentication, requires
non-default passwords, adds per-IP API throttling and security headers, and
runs multiple bounded AI workers. Copy `.env.production.example` to a secure
environment file, replace every placeholder, and run:

```bash
docker compose --env-file .env.production -f compose.production.yml up --build
```

Open `http://localhost:8080`. For an internet deployment, terminate HTTPS at a
managed load balancer or ingress and set `APP_ALLOWED_ORIGINS` to the exact
public site origin. This remains a production-like prototype, not a substitute
for managed backups, centralized logging, malware scanning, or security review.

## Database model

The MySQL schema now covers identity links prepared for Supabase, profiles,
normalized capabilities, availability, Quests, requirements, memberships,
proposals, feedback, blocks, reports, notifications, agent audit trails,
recommendation evidence, idempotency, and an event outbox. Migrations live in
backend/src/main/resources/db/migration.

## Deliberate MVP omissions

- Resume malware scanning and private object storage.
- A trained outcome model; matching is explainable live TF-IDF skill similarity.
- External email, automatic team creation, and sender outbox notifications.
- Centralized throttling, logs, metrics, alerts, managed backups, and load tests.

Supabase JWT authentication, owner/recipient authorization, explicit send
approval, and serialized proposal decisions are implemented. See
`PROTOTYPE_READINESS_REPORT.md` for verified evidence and remaining work.
