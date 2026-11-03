# SentinelCV — Final Security Decision Trace

**System**: SentinelCV Security and Integrity Engine  
**Date**: November 3, 2026  
**Auditor**: Google DeepMind / Antigravity Automated Security Audit  

---

## 1. Authoritative Risk Assessment Decision Precedence

The global assessment engine (`RiskAssessmentService.assessSystemRisk()`) evaluates security findings and mandatory check execution statuses according to a strict, non-bypassable decision hierarchy:

```
                  ┌─────────────────────────────────────┐
                  │ Evaluate Findings & Check Statuses  │
                  └──────────────────┬──────────────────┘
                                     │
                     Critical findings present?
                                ├───► YES ────► QUARANTINE
                                │
                                ▼ NO
                     High findings present?
                                ├───► YES ────► REVIEW
                                │
                                ▼ NO
                Any Mandatory Check FAILED?
                                ├───► YES ────► REVIEW or QUARANTINE
                                │
                                ▼ NO
             Any Mandatory Check UNAVAILABLE?
                                ├───► YES ────► REVIEW
                                │
                                ▼ NO
            Any Mandatory Check NOT_EXECUTED?
                                ├───► YES ────► REVIEW
                                │
                                ▼ NO
               Missing Mandatory Check Status?
                                ├───► YES ────► REVIEW
                                │
                                ▼ NO
           All Mandatory Checks Explicitly PASSED
           and Bound to Exact Current Assets?
                                └───► YES ────► ACCEPT
```

### Mandatory Security Checks Matrix

| Check Name | Target Asset | Freshness Validation Rule | Stale Fallback Status |
| :--- | :--- | :--- | :--- |
| `model.baseline_inference` | Active `MlModel` | Matches `sha256Hash`, file exists, audit is `COMPLETED`, no newer failed/running audit | `NOT_EXECUTED` (yields `REVIEW`) |
| `model.trigger_perturbation` | Active `MlModel` | Matches `sha256Hash`, file exists, audit is `COMPLETED`, no newer failed/running audit | `NOT_EXECUTED` (yields `REVIEW`) |
| `dataset.integrity` | Active `Dataset` | Matches `sha256Hash`, directory exists, audit is `COMPLETED`, no newer failed/running audit | `NOT_EXECUTED` (yields `REVIEW`) |
| `provenance.verification` | Inference Chain | Non-empty records, `assetHash` matches head record hash (`currentRecordHash`) | `NOT_EXECUTED` (yields `REVIEW`) |

---

## 2. Invalidation & Freshness Trace Scenarios

### Scenario A: Empty Database or Unaudited Assets
- **Preconditions**: Database initial state, no audits run.
- **Trace**:
  1. `assessSystemRisk()` iterates over `MANDATORY_CHECKS`.
  2. `checkStatusRepository.findById(checkName)` is empty.
  3. Status resolves to `NOT_EXECUTED`.
  4. Decision engine tallies: `notExecutedChecksCount = 4`.
- **Verdict**: `REVIEW` (Zero false `ACCEPT`).

### Scenario B: Asset Modification Post-Audit
- **Preconditions**: Model ID 1 successfully audited (`PASSED` stored with hash `H1`). Model file updated or hash changed to `H2`.
- **Trace**:
  1. `validateFreshnessForPassedCheck` fetches Model ID 1.
  2. Model hash `H2` is compared against recorded `H1`.
  3. `H2.equalsIgnoreCase(H1)` returns `false`.
  4. Status downgraded immediately to `NOT_EXECUTED`.
- **Verdict**: `REVIEW`.

### Scenario C: Newer Audit Interrupted or Failed
- **Preconditions**: Audit 1 for Model ID 1 completed with `PASSED`. Audit 2 initiated and interrupted (`RUNNING`) or terminated with error (`FAILED`).
- **Trace**:
  1. `validateFreshnessForPassedCheck` finds `audits.stream().max(Comparator.comparing(ModelAudit::getId))`.
  2. Latest audit ID (Audit 2) > recorded Audit 1.
  3. If Audit 2 is `FAILED` -> returns `FAILED`.
  4. If Audit 2 is `RUNNING` or `PENDING` -> returns `NOT_EXECUTED`.
- **Verdict**: `FAILED` / `REVIEW` (Old `PASSED` can never override newer execution).

---

## 3. Archive Confinement & Overwrite Defense Trace

### Phase 1: Ingestion & Magic Bytes
1. Read first 4 bytes of input stream.
2. Verify against `0x50 0x4B 0x03 0x04`, `0x50 0x4B 0x05 0x06`, or `0x50 0x4B 0x07 0x08`.
3. Reject invalid headers with `SentinelCvException("INVALID_FILE_TYPE")`.

### Phase 2: Pre-Extraction Destination Tree Scan
1. Traverse target destination path hierarchy upwards to filesystem root: check `LinkOption.NOFOLLOW_LINKS` and reject if symlink.
2. If destination directory exists, execute `Files.walkFileTree` with `LinkOption.NOFOLLOW_LINKS`.
3. If any file or directory inside destination is a symlink, reject immediately with `SentinelCvException("SECURITY_VIOLATION")`.

### Phase 3: Staged Confinement & Resource Quotas
1. Extract into isolated temporary staging directory `sentinelcv_stage_XXXXXX`.
2. Verify entry count <= 10,000.
3. Verify entry size <= 100 MB.
4. Verify total uncompressed extraction <= 500 MB.
5. Verify compression ratio <= 100:1 (enforced on both total extraction and per-entry).
6. Verify no duplicate entry names.
7. Verify all staged paths resolve within staging directory.

### Phase 4: Atomic Kernel-Level Commit
1. Verify pre-existing files snapshot to prevent destructive rollback.
2. For each extracted file, open channel using:
   ```java
   FileChannel.open(targetF, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)
   ```
3. If file exists, kernel atomically aborts (`O_CREAT | O_EXCL`) and throws `FileAlreadyExistsException`.
4. Catch exception and throw `SentinelCvException("DESTINATION_COLLISION")`.
5. On failure, rollback removes only items created during the current extraction operation; pre-existing unrelated user data is preserved untouched.

---

## 4. Cryptographic Provenance Key Security Trace

### Resolution Pipeline
1. Check configured property `sentinelcv.provenance.hmac-key`. If present, strictly validate 64-character hex format.
2. Check environment variable `SENTINELCV_HMAC_KEY`. If present, validate 64-character hex format.
3. Check local key file `./data/keys/provenance.key`:
   - Verify `provenance.key` is NOT a symlink (`NOFOLLOW_LINKS`).
   - Verify `./data/keys` directory is NOT a symlink (`NOFOLLOW_LINKS`).
   - On POSIX: verify file permissions equal `0600` (`rw-------`). If mismatch, attempt correction; if still mismatch, throw `IllegalStateException`.
   - On POSIX: verify directory permissions equal `0700` (`rwx------`). If mismatch, throw `IllegalStateException`.
   - Validate 64-character hex format without leaking key bytes in exceptions.
4. On generation: create temp file with `0600` permissions attribute, write key, atomically move to `keyPath`, and read back permissions to guarantee fail-closed enforcement.

---

## 5. Provenance Batch Null-Safety & Classification Trace

1. **Null/Empty JSON Input**:
   - `importAndVerify(null)` or `importAndVerify("null")` returns `VerificationBatchResult` with `totalRecordsChecked = 0`, `assessmentDecision = "UNAVAILABLE"`.
   - Records `CheckExecutionStatus.UNAVAILABLE` in `RiskAssessmentService`.
2. **Batch Containing Null Elements**:
   - `InferenceValidator` isolates non-null records and tallies null records.
   - For each null element: increments `failedCount`, creates `ProvenanceVerification("MALFORMED_NULL_RECORD")` with `FAILED` status, and adds `FailureCategory.MALFORMED_RECORD`.
   - Resulting decision: `hasIntegrityCompromise = true` or `failedCount > 0`, producing `FAILED` / `QUARANTINE`. `VERIFIED` is strictly unreachable.
3. **Controller Safety**:
   - `InferenceController` redirects with flash error without throwing 500 NPE.

---

## 6. Async Executor Saturation Trace

```
Incoming Audit Request -> Controller -> Check if Audit PENDING/RUNNING?
                              │
                    ┌─────────┴─────────┐
                   YES                  NO
                    │                   │
             Reject duplicate     Create PENDING audit in DB
             Flash error message        │
                                  Submit to auditTaskExecutor
                                        │
                         Is thread pool/queue saturated?
                                        │
                            ┌───────────┴───────────┐
                           YES                      NO
                            │                       │
                     AbortPolicy throws        Worker thread picks up
                 RejectedExecutionException    Background audit runs
                            │
                     Catch in Controller:
                     - Audit status -> FAILED
                     - Check status -> FAILED
                     - Error flash -> Queue saturated
                     - HTTP response -> 302 / 503
```
Worker threads in HTTP server (Tomcat/Jetty) are completely protected and never blocked.

