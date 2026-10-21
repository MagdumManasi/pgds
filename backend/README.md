# PGDS Backend - Sprints 1-5

AI-Powered Public Grain Distribution System. Java 21, Spring Boot 3.3, MySQL 8, JWT.

## Run
Option A - everything in Docker:
    docker compose up --build

Option B - local:
    docker compose up mysql -d
    mvn spring-boot:run

## Try it
    curl -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
         -d '{"username":"admin","password":"Pgds@12345"}'
    curl localhost:8080/api/users/me -H "Authorization: Bearer <token>"

Demo users (password Pgds@12345): admin, officer, warehouse, dealer, auditor, beneficiary.
Set SEED_DEMO=false and a real JWT_SECRET in production.

## Sprint plan
| Sprint | Scope |
|---|---|
| 1 | Project setup, Docker, entities, JWT + RBAC (done) |
| 2 | Beneficiary and ration card CRUD, entitlement calculation, warehouse/FPS ledger APIs (done) |
| 3 | Distribution workflow with validation + receipts, complaints, officer dashboard APIs (done) |
| 4 | AI service: anomaly detection, demand prediction, stock-exhaustion forecast, complaint classifier (done) |
| 5 | React app: login, role dashboards (GrainGuard layout), Chart.js (done, see pgds-frontend) |
| 6 | Analytics/reports, testing, hardening, deployment, documentation |

## Sprint 2 API summary
| Method + path | Roles | Purpose |
|---|---|---|
| POST/GET /api/admin/warehouses, /api/admin/fps | SUPER_ADMIN | Master data |
| POST/GET/PUT /api/officer/ration-cards | ADMIN, OFFICER | Card CRUD; response includes monthly entitlement |
| PATCH /api/officer/ration-cards/{id}/eligibility?eligible=false | ADMIN, OFFICER | Deactivate / reactivate a card |
| POST/GET/DELETE /api/officer/beneficiaries | ADMIN, OFFICER | Members; blocks duplicates and over-capacity cards |
| GET /api/beneficiary/me, /api/beneficiary/shops | BENEFICIARY | Own card, entitlement, family, shops in district |
| GET /api/warehouse/{id}/stock, /transactions | ADMIN, OFFICER, AUDITOR, WH MANAGER (own) | Balances and ledger |
| POST /api/warehouse/{id}/receipts, /damage, /transfers | ADMIN, WH MANAGER (own) | Ledger entries; transfer writes OUT + IN atomically |
| GET /api/fps/{id}/stock, /transactions | ADMIN, OFFICER, AUDITOR, FPS DEALER (own) | Shop balances and ledger |
| POST /api/fps/{id}/damage | ADMIN, FPS DEALER (own) | Report damaged stock |

Entitlement (configurable in application.yml): AAY 35 kg per family, PHH 5 kg per person, split by `rice-share`.
Link a beneficiary login to a profile by passing `linkUsername` when adding the beneficiary
(e.g. register a user, then officer adds them with that username).

## Sprint 3 API summary
| Method + path | Roles | Purpose |
|---|---|---|
| GET /api/fps/{id}/cards/{cardNo}/balance | ADMIN, FPS DEALER (own shop) | Entitled / issued / remaining this month |
| POST /api/fps/{id}/distributions | ADMIN, FPS DEALER (own shop) | Issue grain; body `{"cardNumber":"MH-PUN-000001","items":[{"grainType":"RICE","quantityKg":5}]}` returns a receipt |
| GET /api/fps/{id}/distributions | ADMIN, OFFICER, AUDITOR, DEALER (own) | Shop distribution history (paged) |
| GET /api/beneficiary/entitlement, /distributions | BENEFICIARY | Remaining balance and receipt history |
| POST/GET /api/beneficiary/complaints | BENEFICIARY | Submit (auto-classified) and track |
| GET/PATCH /api/officer/complaints | ADMIN, OFFICER | Triage queue; override category/priority/department, set status |
| GET /api/officer/dashboard/summary, stock-by-grain, trend, shops, districts, top-shops | ADMIN, OFFICER | Data for the GrainGuard dashboard cards, donut, line chart, district and shop tables |

Distribution rules: card eligible and assigned to the shop, quantity within this month's remaining entitlement,
shop stock sufficient. The shop row is locked during issue so concurrent requests cannot overdraw stock.
Complaint classification is rule-based now; it sits behind `ComplaintClassifier` so Sprint 4 can swap in an ML model.

Upgrading from Sprint 2: the distributions table changed (receipt number is no longer unique). Reset the dev database:
`docker compose down -v && docker compose up --build`.

## Sprint 4: AI service
| Method + path | Roles | Purpose |
|---|---|---|
| POST /api/admin/ai/demo-history?fpsId=1 | SUPER_ADMIN | Generates ~5 months of synthetic history + injected anomalies (disable with AI_DEMO_ENDPOINT=false) |
| POST /api/officer/ai/run | ADMIN, OFFICER | Run all AI checks now (also runs nightly at 02:00 IST) |
| GET /api/officer/ai/alerts?status=OPEN | ADMIN, OFFICER | AI alerts (auditors: /api/audit/ai/alerts) |
| PATCH /api/officer/ai/alerts/{id} | ADMIN, OFFICER | Review: `{"status":"RESOLVED","note":"..."}` |
| GET /api/officer/ai/stock-forecast?type=FPS or WAREHOUSE | ADMIN, OFFICER | Days until each grain runs out |
| GET /api/officer/ai/demand | ADMIN, OFFICER | Next-month demand per shop and grain, with shortfall vs stock |
| POST /api/officer/ai/classify | ADMIN, OFFICER | Try the complaint classifier: `{"text":"..."}` |
| GET /api/officer/ai/model-info | ADMIN, OFFICER | Classifier algorithm, training size, cross-validation accuracy |

Models
- Fraud / anomaly: custom Isolation Forest (Liu et al. 2008) on 4 features per issue (quantity/entitlement, issues per card per month, hour of day,
  shop issues per hour) plus hard rules (off-hours, >= 25 issues per shop-hour).
- Demand: least-squares trend blended with recent mean, optional seasonal index, capped by entitlement requirement.
- Stock exhaustion: balance / average daily outflow over 30 days. CRITICAL <= 3 days, WARNING <= 7, WATCH <= 14.
- Complaints: TF-IDF + Multinomial Naive Bayes (Weka), trained at startup from src/main/resources/ai/complaints_training.csv,
  with rule-based fallback. Add more rows to the CSV to improve accuracy.

Demo flow: login as admin -> POST /api/admin/ai/demo-history -> login as officer -> POST /api/officer/ai/run -> GET /api/officer/ai/alerts.
Reset the dev database first (`docker compose down -v`) because Sprint 4 adds the ai_alerts table and receipts changed in Sprint 3.

## Sprint 5 backend additions (for the frontend)
- GET /api/warehouse/{id}/shops - shops served by the warehouse (transfer form)
- GET /api/officer/fps - shop picker for the ration card form
