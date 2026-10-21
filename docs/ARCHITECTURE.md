# Architecture

## Layers (matches Fig. A of the synopsis)
React SPA -> HTTPS + JWT -> Spring Boot (controllers -> services) -> Spring Data JPA / Hibernate -> MySQL.
The AI service is part of the same Spring Boot application and reads the same tables.

## Packages (backend/src/main/java/com/pgds)
| Package | Responsibility |
|---|---|
| security, auth, user | JWT filter, Spring Security rules, login lockout, staff and beneficiary accounts, change password |
| domain, repo | JPA entities and repositories |
| card, entitlement | Ration cards, members (duplicate guard), NFSA entitlement calculation |
| stock | Append-only stock ledger for warehouses and shops (receipt, transfer, damage) |
| distribution | Issue grain: eligibility, monthly limit, stock check, receipt |
| complaint | Complaints and classification (rule-based + Weka ML) |
| dashboard, reports | Officer dashboard aggregates, monthly / district / shop / complaint reports, CSV export |
| assistant | RAG assistant: knowledge base, retrieval, Spring AI chat, role-scoped live data |
| ai | Isolation Forest, anomaly detection and evaluation, demand model, stock-exhaustion forecast, alerts, nightly scheduler |
| master, portal, seed, common | Warehouse/shop master data, beneficiary portal, demo seeding, error handling |

## Key design decisions
1. **Stock is a ledger, never a number that is overwritten.** Balance = Opening + Received + Transfer in - Transfer out - Distributed - Damaged,
   computed from `stock_transactions`. Every movement records who, when and why, giving a complete audit trail.
2. **No overdraw, no double issue.** Outflows lock the source row (`SELECT ... FOR UPDATE`) before checking the balance. Distribution also
   locks the shop, so concurrent terminals cannot exceed stock or a card's monthly entitlement.
3. **Entitlement is derived, not stored.** AAY 35 kg/family, PHH 5 kg/person (configurable), split rice/wheat by `rice-share`.
4. **Roles are enforced twice**: URL rules in `SecurityConfig` and object-level checks in `AccessGuard` (a warehouse manager only touches
   their own warehouse, a dealer only their own shop). Roles come from the signed JWT; the database is the source of truth for ownership.
5. **AI is advisory.** Models raise alerts for humans to review (OPEN -> UNDER_REVIEW -> RESOLVED / DISMISSED); they never block a distribution.
   Alerts carry a unique reference key, so re-running analysis never duplicates them.

## AI components
| Task | Method | Notes |
|---|---|---|
| Fraud / anomaly | Isolation Forest (custom Java, Liu et al. 2008) + hard rules | Features: quantity/entitlement, issues per card per month, hour, shop issues per hour. Score >= 0.65 flags. |
| Demand | Least-squares trend (60%) + mean of last 3 months (40%), seasonal index when a year exists | Capped at the entitlement requirement of the shop's cards. Falls back to 85% of requirement with no history. |
| Stock exhaustion | Balance / average daily outflow (30 days) | CRITICAL <= 3 days, WARNING <= 7, WATCH <= 14. |
| Complaints | TF-IDF + Multinomial Naive Bayes (Weka), rule-based fallback | Trained at startup from `ai/complaints_training.csv`. |

## AI assistant (RAG)
`assistant` package: knowledge-base notes -> passages -> Spring AI vector store (or keyword search) -> Spring AI ChatClient, with role-scoped live data
added to the context. Works with no model in `local` mode. See `ASSISTANT_RAG.md`.

## Data model (main tables)
users, warehouses, fair_price_shops, ration_cards, beneficiaries, stock_transactions, distributions, complaints, ai_alerts.
