# Contract changes

Every change to `shared/` gets a line here, newest first. Format:
`YYYY-MM-DD · agent · additive|breaking · what · why`

- 2026-10-14 · frontend · additive · `domain/ExpenseSummary.kt`: `summarizeExpenses(baseCurrency, budgetMinor, expenses, timeZone)` returning `ExpenseSummaryDto` · the app computes the Expenses summary locally (Phase 1) and the server (B2.7) must give the same numbers, so the rule lives in `shared`. Backend: use it for `GET /trips/{id}/expenses/summary` instead of re-implementing.
- 2026-10-14 · setup · initial · ApiRoutes, DTOs, enums, ErrorCode, Timestamp serializer, domain (reorder, scheduleAtEndOfDay, splitEqually, computeBalances, fewestTransfers, convertMinor, checkPassword, generateInviteCode) with tests · handoff package from the design phase. Endpoints added on top of the original TRD table: DELETE packing item, PUT/DELETE expense, POST /auth/logout, GET invites, GET settlements, DELETE recommendation feedback (undo). `RecommendationRequest.newSearch=false` (background next batch) does not count against the daily quota.
