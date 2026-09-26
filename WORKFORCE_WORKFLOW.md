# Workforce orchestration prototype

The existing CHIMAERA application now focuses on converting project descriptions
into skill requirements, recommending employees, constructing a team, and
allocating work. The existing dark/crimson interface, sign-in, profiles, resumes,
and invitation inbox are retained.

## Workflow

1. Employees enter skills in My profile and save their years of experience,
   weekly capacity, and hours committed to other work in Workforce capacity.
   Capacity defaults to zero until supplied; no employee availability is invented.
2. Describe a project in Plan project. The extractor recognizes skill names in
   the live skill catalog and combines them with explicitly supplied skills.
3. Review suggested skill workstreams. Edit task names, skills, hour estimates,
   and team size. Four-hour initial estimates are editable placeholders.
4. Recommend team. TF-IDF measures skill relevance; each assignment must cover
   all task skills and fit remaining capacity. Equal relevance is resolved by
   experience, free capacity, then stable employee ID. A greedy scheduler handles
   scarce tasks first and exposes unassigned work instead of inventing employees.
5. Approve the displayed allocations. MySQL persists tasks and reserves workload.
   Employee and project locks plus READ_COMMITTED revalidation prevent concurrent
   approvals from double-booking capacity. A project has one approved allocation;
   duplicate approval returns a conflict.
6. Employees see tasks in Inbox and mark them complete to release reserved hours.
   Project owners reopen Your projects to inspect saved assignments and progress.

## Scope

This prototype uses one shared workforce of registered members. Organization is
profile information, not a tenant boundary or manager permission. Profiles that
are inactive, private, or unavailable cannot receive new allocations. Self-
allocation is supported for small-team project owners who also do project work.
Capacity is a current-week planning budget; all outstanding tasks reserve hours
until complete. Calendar scheduling and recurring weekly budgets are not modeled.

The model is unsupervised skill relevance with explicit scheduling constraints,
not a trained prediction of employee performance. General semantic interpretation
of skills absent from the catalog still requires editing the task requirements.
Recommendations require human approval. Task completion does not change legacy
invitation decisions or remove old project/profile/resume data.

Migration V6 adds workforce_profiles and workforce_tasks without rewriting existing
tables. Keep the migration and tables if reverting the UI; do not delete employee
allocations as a rollback step.

## Verification

- Frontend production build passes.
- AI suite: 11 tests pass, including capacity, skill coverage, team size,
  experience tie-breaks, free capacity, and unavailable employees.
- MySQL V6 migration and Hibernate schema validation pass.
- Signed-in UI loads workforce metrics and existing projects.
- Isolated HTTP integration script `backend/tests/workforce-smoke.mjs`: 8 checks
  pass using temporary signed JWTs, a separate H2 memory database, and the live AI
  service. Verified recommendation, owner access restrictions, explicit approval,
  persisted assignments/inbox workload, duplicate rejection, completion permissions,
  workload release, and two simultaneous project approvals respecting capacity.
  The test never accesses the real MySQL database or Supabase accounts.
- Added WorkforceFlowTest for allocation, completion, authorization, and capacity.
  Maven remains blocked by the Windows compiler JAR access error; these Java tests
  have not run through JUnit. The emitted application classes pass the isolated
  HTTP integration checks above. A clean build and a real two-account Supabase
  acceptance test remain required before production deployment.
