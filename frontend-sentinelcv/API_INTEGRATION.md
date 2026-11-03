# API Integration Guide

> **Important:** The Java/Spring Boot backend source was **not available** when this frontend was written.
> Every endpoint, request and response shape below is a **PROPOSAL**, not an existing contract.
> The integrating developer (Antigravity) should inspect the actual controllers and adapt `src/services/*`
> and, where needed, normalise responses so the UI components keep receiving the shapes in *Data the UI expects*.

## How the service layer works

- `src/services/config.js` — reads `VITE_USE_MOCK` and `VITE_API_BASE_URL`.
- `src/services/http.js` — thin `fetch` wrapper (`http()`), `ApiError`, and a mock `delay()`.
- `src/services/{dataset,model,drift,provenance,report,overview}Service.js` — one service per domain. Each method has a mock branch (fixtures from `src/data/`) and a backend branch.
- Pages call services only through `src/services/index.js`; components never call `fetch` directly.

To integrate: set `VITE_USE_MOCK=false`, then edit the backend branch of each method. If the real payloads differ from the shapes below, **map them inside the service** so pages/components do not change.

## Status vocabulary the UI understands

| Concept | Values |
|---|---|
| Decision | `ACCEPT`, `REVIEW`, `QUARANTINE` |
| Check status | `PASSED`, `WARNING`, `FAILED`, `PENDING`, `SKIPPED` |
| Finding severity | `INFO`, `LOW`, `MEDIUM`, `HIGH` |
| Drift severity | `NONE`, `LOW`, `MODERATE`, `HIGH`, `SEVERE` |
| Provenance record status | `VERIFIED`, `FAILED`, `TAMPERED`, `UNAVAILABLE` |
| Provenance chain status | `VALID`, `BROKEN` |
| Report status | `READY`, `GENERATING` |

Unknown values render as a neutral badge showing the raw string.

## Per-page requirements

### Overview — `overviewService.get()`
Proposed `GET /api/overview`
```json
{
  "totals": { "datasets": 4, "models": 4, "accepted": 2, "review": 2, "quarantined": 2 },
  "riskDistribution": [{ "name": "ACCEPT", "value": 2 }],
  "assessmentsByType": [{ "type": "Datasets", "ACCEPT": 1, "REVIEW": 1, "QUARANTINE": 1 }],
  "recentActivity": [{ "id": "a-114", "kind": "Dataset", "subject": "name", "decision": "ACCEPT", "at": "ISO-8601" }],
  "recentFindings": [{ "severity": "HIGH", "title": "…", "subject": "…", "at": "ISO-8601" }]
}
```
May need to be assembled client-side from dataset/model lists if no aggregate endpoint exists.

### Datasets — `datasetService`
| Method | Proposed endpoint | Notes |
|---|---|---|
| `list()` | `GET /api/datasets` | |
| `get(id)` | `GET /api/datasets/{id}` | Currently unused by pages (list contains detail) |
| `register({name, format, file})` | `POST /api/datasets` (multipart) | `format`: `COCO` \| `YOLO`. **Confirm** whether the backend registers by upload or by server-side path. |
| `runAudit(id)` | `POST /api/datasets/{id}/audit` | **Confirm** sync vs async; UI currently awaits then reloads the list. |

Dataset shape: `id, name, format, images, annotations, classes, registeredAt, lastAuditAt, integrity, decision, duplicates{groups,images}, annotationStats{valid,invalid,emptyImages,perClass[{name,count}]}, checks[{name,status,detail}], findings[{severity,title,detail}], history[{id,at,decision,note}], manifestHash`.
Any field may be `null` before the first audit.

### Models — `modelService`
| Method | Proposed endpoint |
|---|---|
| `list()` | `GET /api/models` |
| `get(id)` | `GET /api/models/{id}` |
| `register({name, file})` | `POST /api/models` (multipart, `.onnx`) |
| `runAudit(id)` | `POST /api/models/{id}/audit` |

Model shape: `id, name, format, sizeMb, opset, producer, registeredAt, lastAuditAt, decision, inputs[{name,shape,dtype}], outputs[…], inspection{compatible,parameters,nodes,unsupportedOps[],externalData}, repeatability{runs,identical,maxDeviation,status}, perturbation{status,trials[{name,stability}]}, findings[…], history[…], fileHash`.
**Confirm** what the backend's repeatability/perturbation results actually contain; the UI's "stability %" per trial is an assumed presentation.

### Drift — `driftService.list()`
Proposed `GET /api/drift/comparisons`. Shape: `id, name, referenceLabel, currentLabel, computedAt, severity, psi, ks{statistic,pValue}, features[{name,psi,ks,pValue,severity,data[{bin,reference,current}]}], trend[{date,psi}]`.
- `features[].data` (binned distributions) and `trend` (history) are **only shown if provided**; confirm whether the backend persists/returns them. If not, those charts need to be hidden or removed.
- The 0.10 / 0.25 PSI reference lines are the conventional rule of thumb, **not** known backend thresholds. Confirm or remove.

### Provenance — `provenanceService`
| Method | Proposed endpoint |
|---|---|
| `list()` | `GET /api/provenance/records` → `{ chain, records }` |
| `verifyChain()` | `POST /api/provenance/verify` → same shape |

`chain`: `{ id, totalRecords, status: "VALID"|"BROKEN", firstBrokenIndex, note }`.
`records[]`: `id, index, model, input, topConfidence, status, recordedAt, inputHash, outputHash, previousHash, recordHash, hmac, hmacMatch (true|false|null), linkValid, reason`.
**The frontend does not verify anything cryptographically.** Statuses must come from the backend's verification result. Confirm how the backend exposes HMAC key availability (drives `UNAVAILABLE`).

### Reports — `reportService`
| Method | Proposed endpoint |
|---|---|
| `list()` | `GET /api/reports` |
| `download(report)` | `GET /api/reports/{id}/download` (binary) |

Report shape: `id, title, type ("Dataset"|"Model"|"Drift"), subject, generatedAt, status, decision, format, sizeKb, summary, sections[]`.
The preview panel renders `summary` and `sections`. **Confirm** what format the backend generates (HTML/PDF/JSON) and what preview data exists; the preview may need to embed the backend's rendered report instead.

## Operations that are mock-only today

- Dataset and model **registration** (file is not uploaded; an in-memory record is added).
- **Run audit** for datasets and models (writes a clearly labelled "Simulated (mock mode)" result; no analysis occurs).
- **Verify chain** (returns the fixture unchanged).
- **Report download** (returns a text file marked MOCK).
- All list/detail data (fixtures in `src/data/`). Mock state resets on page reload.

## Contracts that must be confirmed

1. Actual REST paths, HTTP verbs, and whether the backend exposes JSON APIs at all (the current UI is server-rendered Thymeleaf) — controllers may need `@RestController` endpoints.
2. Authentication/session and CSRF handling (not implemented in the frontend).
3. CORS configuration for `http://localhost:5173`, or a Vite dev-server proxy.
4. Upload size limits and multipart field names.
5. Whether audits are synchronous; if asynchronous, add polling (hook point: `runAudit` in the dataset/model services).
6. Decision, severity and status enum spellings.
7. Date/time format (assumed ISO-8601).
8. Pagination (the frontend currently loads full lists and sorts/filters client-side).
9. Error response format (`http.js` surfaces status plus first 200 characters of the body).
10. Whether duplicate-detection results include per-file listings (the Datasets drawer currently shows counts only).

## Suggested integration steps

1. Copy `.env.example` → `.env`, set `VITE_USE_MOCK=false` and the backend URL.
2. Add CORS or a Vite proxy.
3. For each service method: read the real controller, replace the endpoint path, and add a mapper so the returned object matches the shapes above.
4. Remove or hide UI that the backend cannot support (e.g. drift trend, perturbation bars) rather than leaving mock-looking placeholders.
5. Re-check that the "Mock mode" banner disappears (it is driven by `VITE_USE_MOCK`).
