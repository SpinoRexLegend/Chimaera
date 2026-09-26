# CHIMAERA database

MySQL 8.4 is the production system of record. Flyway owns the schema; Hibernate
validates mappings and never mutates production tables.

## Migration order

1. V1 creates user identity, future Supabase identity links, normalized skills,
   interests, and availability.
2. V2 creates Quests, capability requirements, team membership, proposals,
   feedback, blocking, moderation, and notifications.
3. V3 creates agent audit, recommendation evidence, transactional outbox, and
   idempotency tables.

The current MVP remains compatible with quests, quest_requirements, and
collaboration_proposals. Nullable identity columns are an intentional
compatibility window until Supabase authentication is introduced.

## Future Supabase authentication

Store the verified Supabase JWT sub value in auth_identities.subject with
provider set to supabase. Never accept a user ID from the browser as authority.
Resolve the authenticated subject to users.id in Spring Security and use that
internal UUID for authorization and foreign keys.

## Rollback

Migrations are additive and the existing local H2 file is not modified. Roll
back the application by restoring the previous release and selecting the local
profile. Do not run Flyway clean or manually drop tables in production. Correct
forward with a new migration after data has been written.
