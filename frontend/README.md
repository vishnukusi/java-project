# SentinelCV Frontend

React + Vite frontend for **SentinelCV — an offline-first computer vision security assessment platform**.
It replaces the Thymeleaf UI of the existing Spring Boot backend. It contains two experiences:

- **`/`** — a scroll-driven product landing page (the story).
- **`/dashboard/*`** — the operational application (the product), with six sections.

> **Status: mock mode by default.** All data is sample fixture data. Nothing here is a real audit result,
> and no backend API contract is assumed to exist. See `API_INTEGRATION.md`.

## Quick start

```bash
npm install
cp .env.example .env     # optional; defaults to mock mode
npm run dev              # http://localhost:5173
npm run build            # production build → dist/
npm run preview          # serve the production build locally
```

Requires Node 18+.

## Routes

| Route | Page |
|---|---|
| `/` | Product landing page |
| `/dashboard` | Overview |
| `/dashboard/datasets` | Datasets (registration, list, audit details, history) |
| `/dashboard/models` | Models (registration, ONNX inspection, behavior, findings, history) |
| `/dashboard/drift` | Drift Monitoring (PSI, KS, feature-level drift, trend) |
| `/dashboard/provenance` | Inference Provenance (records, hashes, HMAC, chain, tampering) |
| `/dashboard/reports` | Reports (list, preview, download) |
| `*` | Not-found page |

## Stack

React 18 · Vite 5 · JavaScript/JSX · Tailwind CSS 3 · Lucide React · Framer Motion · Recharts · React Router 6.

**About shadcn/ui:** the UI primitives in `src/components/ui/` (button, card, input, dialog, drawer, toast, skeleton)
are hand-written in the shadcn/ui style (Tailwind + `cn()` helper, same file/API conventions) rather than generated with the
shadcn CLI. They do not depend on Radix. You can swap in generated shadcn components later without touching pages.

## Project structure

```
src/
  components/
    ui/          # primitives (button, card, input, overlays, toast, skeleton)
    common/      # StatusBadge, DataTable helpers, States (error/empty/mock banner), Tabs, Logo, PageHeader
    landing/     # Hero, Problem, Workflow, Capabilities, Architecture, FinalCTA, motion helpers
    datasets/ models/ provenance/   # page-specific detail views and dialogs
  layouts/       # LandingLayout, DashboardLayout (collapsible sidebar, breadcrumbs, route transitions)
  pages/         # one file per route
  services/      # centralized API layer (mock/backend switch)
  hooks/         # useAsync, useSortFilter
  data/          # mock fixtures (clearly labelled)
  utils/         # cn, formatters
```

## Mock mode vs backend mode

Controlled by `.env` (see `.env.example`):

```
VITE_USE_MOCK=true                      # default: bundled fixtures, nothing leaves the browser
VITE_API_BASE_URL=http://localhost:8080 # used only when VITE_USE_MOCK=false
```

In mock mode a banner is shown on every dashboard page. In backend mode each service calls **proposed** endpoints that
must be confirmed against the Java controllers (see `API_INTEGRATION.md`).

## Honesty rules built into the UI

- The frontend never computes or verifies SHA-256/HMAC and never runs audits; it only displays results.
- Mock "run audit" actions are simulated placeholders and say so.
- Mock hashes are pseudo-random strings, not real digests. Mock report downloads are text files marked `MOCK`.
- Provenance statuses (Verified / Verification failed / Tampering detected / Verification unavailable) come from data, never from the UI.

## Animation and accessibility

- Landing animations use transform/opacity only, trigger once on scroll, and never hijack scrolling.
- `prefers-reduced-motion` is honoured globally (`MotionConfig reducedMotion="user"`, CSS media query, and scripted demos skip to their end state).
- Dashboard motion is limited to short route fades, drawers/dialogs and chart entry.

## Build and verification results (when this package was created)

- `npm run build` — succeeded (Vite 5, 2,582 modules).
- Headless Chromium smoke test against the production build: all 7 routes plus a 404 route rendered with the expected headings; the landing "Open Dashboard" CTA navigated to `/dashboard`.
- The only console errors observed were failed requests to Google Fonts (the test sandbox had no access to it); the app falls back to system fonts.
- No automated unit tests are included.

## Known limitations

- All data is mock; backend contracts are unconfirmed.
- Uploads in mock mode are not transmitted anywhere.
- Drift history, per-file duplicate listings and report previews depend on what the backend actually exposes.
- The workflow section uses scroll-linked progress (not scroll hijacking); very old browsers may not support it.
- Fonts (Inter, Space Grotesk, JetBrains Mono) load from Google Fonts; for a fully offline deployment, self-host them.
- Responsiveness was checked at a 1360px desktop viewport in the smoke test; narrow/mobile layouts were built responsively but not visually reviewed.
