# CHIMAERA prototype readiness report

Date: 2026-09-26

## Executive result

CHIMAERA is suitable for a controlled working-prototype demonstration on the
current machine. Authentication is enforced, MySQL migrations validate, the
AI service and frontend build pass their automated checks, and the backend now
publishes a health endpoint. It is not approved for an unrestricted public
production launch. External infrastructure, secret rotation, upload malware
scanning, centralized observability, load testing, and a clean Java CI run are
still required.

## Changes completed

| Area | Change | Prototype outcome |
| --- | --- | --- |
| Identity ownership | Removed automatic profile ownership transfer based only on matching email addresses. Supabase JWT `sub` is the identity key. | Prevents email-alias or re-registration takeover of an existing profile. |
| API exposure | Replaced controller-local wildcard localhost CORS with `APP_ALLOWED_ORIGINS`. | Local origin is allowed; an unlisted origin is rejected. |
| HTTP security | Added no-sniff, frame denial, no-referrer and browser permissions headers. | Verified on live backend responses. |
| Health/readiness | Added Spring Boot Actuator health, liveness and readiness support plus graceful shutdown. | `/actuator/health` returns `UP` while MySQL is connected. |
| Actuator access | Only health paths are public; other Actuator paths are denied when authentication is enabled. | Avoids accidental future management endpoint exposure. |
| Request limits | Added maximum skill/candidate collection sizes in Java and Python request models. | Oversized AI matching models are rejected during validation. |
| Proposal safety | Retained pessimistic locking and irreversible, idempotent accept/decline transitions. | Concurrent decisions cannot silently overwrite one another. |
| AI capacity | Retained four-call backend admission control; production image runs bounded Uvicorn workers and concurrency. | Prevents unlimited AI work from consuming all backend/AI resources. |
| Frontend container | Replaced Vite development server with a multi-stage build served by Nginx. | Optimized static assets, SPA fallback and a health route are available. |
| Edge controls | Added Nginx request-size limit, per-IP API throttling, CSP and security headers. | Provides a useful single-instance prototype boundary. |
| Container secrets | Added a production environment template with required non-default MySQL credentials. | Production-like compose fails fast when required variables are absent. |
| Network isolation | Production-like compose does not publish MySQL, backend, or AI ports; only Nginx is exposed. | Internal services are not directly reachable from the host network. |
| Build gate | Backend container build now runs tests instead of skipping them. | A deployable image cannot be produced when Java tests fail. |

## Verification evidence

| Check | Result |
| --- | --- |
| Frontend `pnpm build` | PASS — 468 modules transformed and production assets emitted. |
| AI unit tests | PASS — 6 tests, including oversized-payload validation. |
| Backend startup | PASS — Spring Boot starts on port 8080 against MySQL 8.0.42. |
| Flyway | PASS — all 5 migrations validated; schema is current. |
| Health endpoint | PASS — HTTP 200 with `status: UP` and liveness/readiness groups. |
| Anonymous protected API | PASS — `/api/inbox/proposals` returns HTTP 401. |
| Allowed CORS preflight | PASS — `http://localhost:5173` returns HTTP 200 with the matching allow-origin header. |
| Disallowed CORS preflight | PASS — `https://evil.example` returns HTTP 403. |
| Security headers | PASS — verified no-sniff, frame denial, no-referrer and permissions policy. |
| Java Maven test lifecycle | BLOCKED — source classes are emitted, then this Windows environment denies Java access while closing a dependency JAR. Test execution is not reached. |
| Production compose execution | UNAVAILABLE — Docker is not installed on this machine. Configuration is supplied but was not launched here. |
| Two-account acceptance/resume flow | NOT RUN — requires a second verified Supabase user and must not be simulated against user data without authorization. |

## Required before a public launch

1. Rotate the Supabase secret/service-role key previously shared in chat. It is
   not stored in this project, but disclosure means it must be treated as
   compromised. Store the replacement only in a managed secret store.
2. Run the Java test suite in Linux CI or another unrestricted build host and
   require it before deployment. Add authenticated two-user integration tests.
3. Deploy MySQL as a managed TLS database with automated backups, point-in-time
   recovery, restricted credentials, restore testing, and monitoring.
4. Move resumes to private object storage and add malware scanning, retention,
   audit logging, and short-lived authorized downloads.
5. Add centralized structured logs, metrics, error tracking, alerts, and an
   incident-response runbook. Do not log tokens or resume contents.
6. Put the prototype behind managed HTTPS and centralized rate limiting. The
   included Nginx limiter is per instance and is not sufficient for a cluster.
7. Load-test login, profile, matching, inbox, decisions, and resume downloads;
   test AI/database outages and simultaneous proposal responses.
8. Separate remote AI calls from database transactions before scaling so slow
   AI responses do not reserve database connections.
9. Configure Supabase verified-email policy, password recovery, optional MFA,
   redirect allowlists, session revocation, and account deletion.
10. Add privacy/terms documents, explicit resume-processing consent, data
    export/deletion, and a process for challenging inappropriate AI matches.

## Prototype run paths

- Existing local development UI: `http://localhost:5173`
- Backend health: `http://localhost:8080/actuator/health`
- Production-like container entrypoint after Docker installation:
  `docker compose --env-file .env.production -f compose.production.yml up --build`

The release decision is therefore **working prototype: yes; public production:
no** until the outstanding controls above are verified.
