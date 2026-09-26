# Deploy CHIMAERA on Railway

Railway is the simplest first host for this prototype because one project can
run the React/Nginx frontend, Spring Boot API, Python AI service, and a managed
MySQL database. Only the frontend needs a public domain; the other services can
stay on Railway's private network.

## 1. Put this folder on GitHub

Create an empty GitHub repository, then push the contents of this `chimaera`
folder. Do not commit `.env`, `.env.local`, database dumps, or credentials.

## 2. Create the Railway project

1. Sign in at <https://railway.com> and choose **New Project → Empty Project**.
2. Name it `chimaera`.
3. Add a **MySQL** database from **New → Database → MySQL**. Keep it private.
4. Add three empty services named exactly `ai-service`, `backend`, and
   `frontend`.
5. Connect the same GitHub repository and branch to each service.
6. Set each service's **Root Directory**:
   - `ai-service`: `/ai-service`
   - `backend`: `/backend`
   - `frontend`: `/frontend`

Each directory includes a Dockerfile and a `railway.toml` health-check config.

## 3. Configure variables

Use Railway's **Variables → Raw Editor** for each service. The `${{...}}`
entries below are Railway reference variables; paste them literally. Railway
will resolve them without copying database passwords between services.

### ai-service

```dotenv
PORT=8000
WEB_CONCURRENCY=2
```

### backend

The database service below is assumed to be named `MySQL`. If yours has a
different name, choose its variables from Railway's autocomplete.

```dotenv
PORT=8080
SPRING_DATASOURCE_URL=jdbc:mysql://${{MySQL.MYSQLHOST}}:${{MySQL.MYSQLPORT}}/${{MySQL.MYSQLDATABASE}}?useSSL=false&serverTimezone=UTC&characterEncoding=utf8
SPRING_DATASOURCE_USERNAME=${{MySQL.MYSQLUSER}}
SPRING_DATASOURCE_PASSWORD=${{MySQL.MYSQLPASSWORD}}
AI_SERVICE_URL=http://${{ai-service.RAILWAY_PRIVATE_DOMAIN}}:8000
AUTH_ENABLED=true
SUPABASE_ISSUER_URI=https://YOUR_PROJECT_REF.supabase.co/auth/v1
SUPABASE_JWK_SET_URI=https://YOUR_PROJECT_REF.supabase.co/auth/v1/.well-known/jwks.json
APP_ALLOWED_ORIGINS=https://${{frontend.RAILWAY_PUBLIC_DOMAIN}}
HTTP_WORKERS=40
DB_POOL_MAX_SIZE=12
```

### frontend

Use the Supabase **Project URL** and **publishable key**. A publishable key is
safe for a browser bundle; never put a secret/service-role key in this service.

```dotenv
PORT=8080
BACKEND_UPSTREAM=${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080
VITE_SUPABASE_URL=https://YOUR_PROJECT_REF.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=YOUR_PUBLISHABLE_KEY
```

The Vite variables are build-time values. Railway rebuilds after a variable
change, which is required for them to reach the frontend bundle.

## 4. Deploy in dependency order

1. Deploy MySQL and wait for it to become available.
2. Deploy `ai-service`; its health check is `/health`.
3. Deploy `backend`; its health check is `/actuator/health`.
4. Deploy `frontend`; its health check is `/health`.
5. On `frontend`, open **Settings → Networking → Generate Domain**.

Do not create public domains for MySQL, `ai-service`, or `backend`. Browser API
requests use `/api`; Nginx sends them to the private backend service.

## 5. Update Supabase authentication

In Supabase open **Authentication → URL Configuration**:

1. Set **Site URL** to the exact generated frontend URL, such as
   `https://chimaera-production.up.railway.app`.
2. Add the same exact URL to **Redirect URLs**.
3. Keep the localhost redirect only if local development is still needed.

After this, register a fresh test account, sign in, edit a profile, upload a
small PDF resume, send a request, accept it from the recipient account, and
send a chat message.

## 6. Before calling it production

- Attach automated backups to MySQL and test a restore.
- Add error monitoring and uptime checks for the public `/health` endpoint.
- Add malware scanning and private object storage for resumes.
- Add a custom domain and keep HTTPS enabled.
- Use separate Railway `staging` and `production` environments.
- Rotate any credential that has ever been pasted into chat, logs, or source.
- Run load, authorization, and dependency-security tests before real users.

## Rollback

Railway keeps deployment history. If a release fails, open the affected
service's **Deployments** tab and redeploy the last known-good version. Database
schema rollback is separate: Flyway migrations are forward-only here, so take a
database backup before every schema-changing production release.
