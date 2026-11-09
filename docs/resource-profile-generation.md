# Resource search profile generation and retries

JoyHub's default discovery model is `gpt-6.1-terra`. `JOYHUB_AI_MODEL` overrides
this default; an existing deployment pinned to another model needs its environment
updated separately. Translation retains its independent model setting.

The scheduler selects up to 20 eligible documents per pass. Each document is claimed
in a short row-locked transaction with a ten-minute lease. AI and embedding requests
run outside the transaction. Completion checks both the source hash and claim token,
so changes and manual regeneration invalidate older work. Expired leases can be
reclaimed. A source change or explicit regeneration resets the attempt budget.

A generation cycle allows at most four attempts, including the initial attempt.
Transient failures retry after approximately 1, 5, and 25 minutes, with up to 20%
positive jitter. A longer valid relay `Retry-After` takes precedence. HTTP 401/403,
invalid requests, missing models, and known billing/quota failures stop automatic
retries. Correct the configuration and explicitly regenerate to start a new cycle.
Manual regeneration preserves the existing human category.

Migration V61 adds `generation_attempts`, `generation_error_code`,
`generation_next_retry_at`, `generation_lease_token`, and `generation_lease_until`.
Existing FAILED rows retain their category and become eligible for bounded recovery;
READY rows are not regenerated. These fields are database diagnostics; the existing
admin HTTP response/UI has not been expanded in this change.

Failures log the resource identity, attempt, allowlisted error code, and next retry
time. Relay bodies, arbitrary exception messages, source documents, and credentials
are not logged. `PROFILE_INVALID_JSON` includes malformed or non-object profile
responses; valid JSON enclosed in a Markdown code fence is accepted. `LEGACY_UNKNOWN`
means the old worker did not retain a cause. An old FAILED record alone cannot prove
whether the relay timed out, rejected authentication, or returned invalid JSON.

Read-only investigation example (run through the operator's authorized database
access; do not export credentials or documentation):

```sql
SELECT resource_type, resource_id, title, generation_status,
       generation_attempts, generation_error_code, generation_next_retry_at,
       generated_at, category_code, category_source
FROM resource_search_document
WHERE generation_status = 'FAILED'
ORDER BY resource_type, resource_id;
```

Historical failures from the previous worker have no persisted diagnostic cause.
`LEGACY_UNKNOWN` identifies these records until their first new attempt completes.

Roll out the migration and backend together. Existing production model environment
overrides still take precedence over the default. If
rolling the backend back, leave the additive retry columns in place; do not drop them
while new workers are running. Old workers ignore these columns and will not retry
FAILED records.

Validation completed on 2026-11-09:

- `make test-backend-app`: BUILD SUCCESS; 1,438 reported tests, zero failures/errors,
  one skipped. Includes concurrent-claim and transaction-boundary integration tests.
- All V1–V61 SQL migrations applied to a temporary, network-isolated PostgreSQL 16
  container. Fixture checks confirmed bounded recovery for legacy FAILED rows,
  preservation of AUTHOR categories, and no requeue of READY rows.
- Frontend typecheck, lint, deployment contract, and release configuration checks passed.
- `make staging`: healthy backend/web/dependencies; four smoke checks passed, zero failed.
  Legacy local-account/admin-session smoke flows were skipped by the existing script.
- No controller/DTO API contract change.
