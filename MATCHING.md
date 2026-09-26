# Live collaboration matching

Supabase verifies identity; Spring synchronizes profiles and normalized skills to SQL. Requests belong to the verified user. Matching selects active, discoverable profiles with skills, excluding the requester and unavailable users. Only sent proposals appear in the recipient inbox; owners alone can operate on their requests and send proposals.

The Python service fits a scikit-learn TF-IDF model on the live candidate skill lists and ranks cosine similarity. No candidate identities or ranking weights are hardcoded. This is an unsupervised skill-relevance model, not a trained predictor of collaboration success. Historical outcome training, semantic inference of unstated skills, and automatic learning from feedback are not implemented. Explicit required skills are available in the request form. Scores represent similarity, not success probabilities.

Local MySQL runs on 127.0.0.1:3307 with a separate data directory under work/mysql-data. Existing H2 data has not been migrated or deleted. Signing in synchronizes that user's profile into MySQL. Flyway creates the MySQL schema on startup. The local launcher is run-backend-local.ps1; Docker continues to use its own MySQL service on the standard port.

The inbox contains CHIMAERA collaboration proposals, not messages retrieved from an external email provider.

## Inbox responses and concurrency

Recipients can review sent invitations and download the sender's current resume if present. Only the intended recipient may review, respond, or download that file. Drafts remain inaccessible. ACCEPTED and DECLINED responses retain a timestamp in MySQL; repeating the same decision is idempotent, while reversing a decision or resending a answered proposal returns conflict. A pessimistic row lock serializes send/respond transitions. No automatic team membership or external email is created.

Spring's HTTP server already processes requests on multiple threads. HTTP_WORKERS defaults to 40, with bounded connections and backlog; AI calls are capped at four simultaneous calls per API process and have 3-second connection / 15-second read timeouts. This is bounded concurrency, not a production-readiness guarantee. Still required: clean automated tests, two-account acceptance/resume authorization checks, load testing, TLS, rate limiting, monitoring, backups, secret rotation, and malware scanning. External AI calls still occur inside some database transactions and should be separated before scaling; multi-instance AI admission control also needs a shared queue or service-level limit.

Current verification: frontend production build passed and signed-in profile loading was checked in the browser. Backend restarted successfully against MySQL. Added proposal transition tests, but Java test execution remains blocked by Windows compiler dependency-file access errors. The current account has no inbox invitations, so accept/decline and sender downloads are not yet verified end-to-end.

## Profiles and resumes

The workspace's My profile tab loads and edits the SQL profile. Supabase registration metadata initializes new profiles only; later logins preserve edits made in My profile. Email remains managed by Supabase and is read-only here.

Resumes are optional PDFs up to 5 MiB, stored in MySQL's separate user_resumes table. Users may download, replace, or remove their own resume. Candidate cards download resumes through a bearer-authenticated endpoint that checks request ownership and re-runs current eligibility/ranking. Private and unavailable candidates are excluded. There are no public resume URLs. Files are served as attachments with no-store and nosniff headers; the interface explains sharing before upload. PDF header/size checks are not malware scanning. Resume text is not parsed or included in ranking.

Migration V5 only adds a table; it does not change or remove existing user data. To roll back the feature, restore the earlier application while retaining the table and migration history; do not drop uploaded resumes. Scaling beyond this local MVP should move large documents to private object storage and add malware scanning.

Profile/resume verification: frontend build passes, MySQL V5 and Hibernate validation pass, and the signed-in profile view plus saving existing values succeeded in the browser. ProfileResumeTest covers edit persistence, resume lifecycle, eligibility denial, and invalid/oversized files, but automated Java execution remains blocked by the local compiler/file-access issue. Upload/download across two real accounts is not yet verified.

## Verification (2026-09-26)

- Frontend production build passes. Browser checked: complete hero text, visible registration form, working login navigation.
- Python unittest suite: 5 passed (3 live-matching tests, 2 legacy scorer tests).
- MySQL startup: Flyway validates all four migrations and Hibernate schema validation succeeds.
- Frontend responds HTTP 200; anonymous inbox access through the frontend proxy returns HTTP 401.
- Java sources emit compiled classes, but Maven reports Windows AccessDeniedException when closing compiler resources. Surefire also cannot create a dependency-cache directory, so Java integration tests remain unverified. The running local backend uses the emitted classes via `run-backend-local.ps1 -UseCompiledClasses`; this is not evidence of a clean Maven build.
- Real Supabase registration and two-account send/receive have not been exercised with user credentials. Register/verify two accounts with overlapping requested skills, create a request in one account, approve the proposed invitation, then check the other account's inbox.
- The device's reduced-motion preference is enabled; some animations are intentionally limited.
