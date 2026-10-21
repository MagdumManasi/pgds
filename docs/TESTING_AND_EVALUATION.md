# Testing and AI evaluation

## Automated tests (`cd backend && mvn test`)
| Type | What it covers |
|---|---|
| Unit | Entitlement rules, stock transfer rules, distribution rules, JWT, Isolation Forest (outlier scores higher, deterministic), demand model, stock-exhaustion levels, rule and ML complaint classifiers, CSV writer, login lockout |
| Integration (Spring Boot + H2 in-memory + MockMvc) | Authentication and role boundaries, lockout, password change; the full distribution flow (limits, ledger, beneficiary view, dashboard); stock transfers and ownership; demo data -> AI alerts -> evaluation -> reports and CSV |

Integration tests run against H2, not MySQL. Run a manual smoke test on MySQL (docker compose) before a demo.

## Assistant
Unit tests cover passage splitting, keyword retrieval quality (entitlement, complaint routing, alert thresholds) and the rate limiter. Integration tests run the
assistant in local mode and check role scoping (a beneficiary sees only their own entitlement, a dealer only their shop) and access control.
The llm path needs a real model and is not covered by automated tests: test it by hand with Ollama or an API key and compare answers with the cited passages.

## Frontend
`cd frontend && npm run build` must succeed. Manual test: sign in as each role and walk through the demo script in the root README.

## Evaluating the fraud detectors
1. Admin -> Users & Tools -> Generate demo history. This creates normal distributions (business hours, 60-100% of entitlement, once a month)
   plus 34 labelled anomalies: 4 night-time issues and a burst of 30 issues within one hour.
2. AI Alerts -> Evaluate detectors (or `GET /api/officer/ai/evaluation`).
3. The table reports, for Isolation Forest at several thresholds, for the hard rules, and for both combined: flagged, true positives,
   false positives, missed, precision, recall, F1.

How to interpret: the rules catch the injected patterns by construction, so use them as a baseline. The Isolation Forest is unsupervised and
finds patterns without being told them; compare its recall at the chosen threshold (0.65) with the baseline and discuss the precision / recall
trade-off as the threshold moves. Synthetic data demonstrates the method; it is not proof of accuracy on real distribution data.

## Complaint classifier
`GET /api/officer/ai/model-info` shows training size and 5-fold cross-validation accuracy. To improve it, add labelled lines to
`backend/src/main/resources/ai/complaints_training.csv` (format `CATEGORY|text`) and restart.

## Known limitations (state these in the report)
- Demo data is synthetic; demand and anomaly models need months of real data to be trustworthy.
- Duplicate-beneficiary detection is exact (name + Aadhaar last 4); fuzzy matching is future work.
- Login lockout is in-memory and per instance.
- No Aadhaar authentication / biometric POS integration, SMS notification, or offline mode.
- Stock mismatches caused by physical theft cannot be seen unless physical counts are recorded; the system detects ledger and usage anomalies.
