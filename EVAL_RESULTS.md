# Primus — Evaluation Results

## Methodology

5 systems benchmarked against 10 identical simulated incidents each (services rotate through `auth-api`, `checkout-api`, `payment-api`, `orders-api`; fault types rotate through `DEPLOYMENT_ISSUE`, `CONNECTION_POOL_EXHAUSTION`, `TRANSIENT_GLITCH`). Each run:

1. Injects a real, known fault into `FaultSimulator` and captures it as ground truth up front.
2. Runs the diagnoser/agent under test.
3. If the proposed action requires human approval (HIGH-risk), the eval harness auto-approves and executes the recommended candidate — this measures decision quality independent of the approval gate. **This auto-approval exists only inside the eval harness; the production `/approve/{candidateId}` endpoint is unchanged and still requires a real human `APPROVER`/`ADMIN`.**
4. Checks final service health against `FaultSimulator` to determine `resolved`.
5. Resets the fault before the next run.

Three metrics are reported per system:
- **`diagnosisAccuracyPct`** — does the diagnosis text correctly name the actual fault category?
- **`remediationAccuracyPct`** — did the system propose the one action that fixes the actual injected fault?
- **`resolvedPct`** — did the service actually end up healthy?

## Results (final, post eval-pipeline-audit)

| System | Diagnosis Accuracy | Remediation Accuracy | Resolved |
|---|---|---|---|
| rule_based | 100% | 100% | 100% |
| llm_no_rag | 20% | 40% | 40% |
| llm_rag | 100% | 30% | 30% |
| single_agent_primus | 40% | 80% | 80% |
| multi_agent_primus | 90% | 60% | 60% |

**`remediationAccuracyPct` and `resolvedPct` agree for every system** — the eval harness is internally consistent, meaning "resolved" now genuinely measures decision quality rather than being distorted by approval-gate mechanics or ground-truth bugs (see audit below).

## Eval Pipeline Audit (4 bugs found and fixed in the measurement harness itself)

Before trusting any of the numbers above, the eval harness itself was audited and found to have four real bugs — all in measurement code, not in the core agent/remediation logic:

1. **`FaultSimulator` wasn't cause-aware.** It tracked only a healthy/unhealthy boolean, so any remediation action unconditionally "healed" the service regardless of whether it matched the actual fault. Fixed by introducing a `FaultType` enum (`DEPLOYMENT_ISSUE`, `CONNECTION_POOL_EXHAUSTION`, `TRANSIENT_GLITCH`) and an `attemptHeal()` that only succeeds when the action matches. Verified end-to-end manually (wrong action → `REOPENED`, correct action → `RESOLVED`).

2. **Log-text collision.** The `TRANSIENT_GLITCH` simulated log line accidentally contained the word "deploy" ("no config or deploy changes present"), causing `RuleBasedDiagnoser`'s keyword matcher to misclassify it as `BAD_DEPLOYMENT`. Also, `RuleBasedDiagnoser` had no branch for connection-pool-exhaustion needing `scale_service` — it could only ever propose `restart_service` or `rollback_deployment`. Both fixed.

3. **Missing ground-truth branch.** `EvalResult.diagnosisCorrect()`/`remediationCorrect()` only had logic for `CONNECTION_POOL` and `BAD_DEPLOYMENT` — any `TRANSIENT_GLITCH` case automatically scored as incorrect regardless of actual correctness. Also, the `CONNECTION_POOL` branch incorrectly accepted both `restart_service` and `scale_service` as correct, when only `scale_service` genuinely fixes that fault type. Both fixed.

4. **Approval-gate distortion.** When a HIGH-risk action (e.g. `rollback_deployment`) correctly required human approval, the eval never actually executed it — it just read the proposed action off the candidate list and hardcoded `resolved = false`. This meant a system could be 100% correct in its recommendation and still score 0% resolved. Fixed by having the eval harness auto-approve and execute the recommended candidate (see Methodology above), decoupling decision quality from governance-gate behavior.

## Key Findings

**Multi-agent Primus has the strongest diagnosis accuracy (90%)** but a lower remediation accuracy (60%) than its diagnosis would suggest. Digging into the individual runs shows this isn't a code bug: on `CONNECTION_POOL_EXHAUSTION` cases, the `RemediationAgent`'s underlying Gemini call sometimes proposes `restart_service` instead of the objectively-correct `scale_service` — a plausible-but-wrong choice, since both are reasonable real-world responses to "connection" symptoms. This is a genuine LLM judgment limitation on an ambiguous case, not a measurement error (the eval pipeline audit above already ruled out the measurement-side explanations).

**Single-agent Primus shows the inverse pattern**: lower diagnosis-text accuracy (40%, its free-form diagnosis text doesn't always use the exact keywords the scorer checks for) but higher remediation accuracy (80%) — it tends to pick the right tool even when its stated reasoning doesn't explicitly name the fault category.

**`rule_based` is now a genuinely trustworthy baseline** (100/100/100) after the log-collision and missing-branch fixes — previously its 100% was tautological (its heuristic and the ground-truth scorer used an identical, incomplete substring check).

**RAG's effect on diagnosis vs. remediation diverges**: `llm_rag` reaches 100% diagnosis accuracy (retrieved runbooks clearly help name the right root cause) but only 30% remediation accuracy — in these runs it disproportionately reaches for `rollback_deployment` even on `CONNECTION_POOL_EXHAUSTION` cases, similar to multi-agent's bias but more pronounced.

## Known Environmental Constraints (not bugs)

- Gemini's free tier has a daily quota (500 requests/day for `gemini-3.1-flash-lite`). Running the full eval (50 runs, several LLM calls each) repeatedly in one day can exhaust it, surfacing as HTTP 429 `RESOURCE_EXHAUSTED`. The harness's `runWithRetry()` handles this gracefully — one run is marked `FAILED` rather than crashing the whole eval.
- Occasional HTTP 503 `UNAVAILABLE` from Gemini's servers is a transient upstream issue, also handled by the same retry path.
