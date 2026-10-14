# RANSEL — handoff package

Starting point for building RANSEL with two Claude Code agents (backend and frontend). Copy everything in this folder into the root of the repository.

```
CLAUDE.md                     rules for both agents (read first)
docs/                         PRD, TRD, App Spec, tasks per agent, design screens, IRIS tokens, specs
shared/src/                   API contract: routes, DTOs, enums, error codes, domain logic + tests
shared/CONTRACT_CHANGES.md    log of contract changes
server/CLAUDE.md              backend agent rules
server/src/main/resources/db/migration/   V1–V4 (PostgreSQL, tested on 16)
composeApp/CLAUDE.md          frontend agent rules
composeApp/src/commonMain/kotlin/app/ransel/ui/theme/   IrisTheme (colors, type, shapes, spacing)
```

## Cara mulai (untuk SEN)

1. Generate proyek di kmp.jetbrains.com: centang Android, iOS, Web, Server. Package `app.ransel`, nama proyek `ransel`.
2. Salin isi paket ini ke root repo (timpa kalau ada nama file yang sama), lalu commit.
3. Siapkan font Plus Jakarta Sans (400/600/700/800, TTF) dan ilustrasi kapibara dari IRIS, atau minta agent frontend menyiapkan placeholder dulu.
4. Jalankan dua sesi Claude Code di repo yang sama (sebaiknya di branch/worktree terpisah):
   - **Frontend:** "Kamu agent frontend RANSEL. Baca CLAUDE.md dan composeApp/CLAUDE.md, lalu kerjakan docs/tasks/frontend.md mulai dari Setup. Berhenti dan lapor setiap selesai satu fase."
   - **Backend:** "Kamu agent backend RANSEL. Baca CLAUDE.md dan server/CLAUDE.md, lalu kerjakan docs/tasks/backend.md mulai dari Phase 2. Berhenti dan lapor setiap selesai satu fase."
5. Agent frontend mengerjakan F0 (setup) dulu supaya modul `shared` bisa dipakai keduanya. Agent backend boleh mulai B2.1 setelah F0.2 selesai.
6. Untuk deploy (akhir Fase 2), siapkan akses VPS dan domain `ransel.app` (DNS `api.ransel.app` → IP VPS).
