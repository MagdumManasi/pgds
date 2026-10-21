# API reference

Base URL `http://localhost:8080`. Authenticate with `POST /api/auth/login`, then send `Authorization: Bearer <token>` (valid 2 hours).
Errors: `{"timestamp","status","error","details"?}`. 400 validation, 401 not signed in, 403 role/ownership, 404, 409 business rule, 429 locked out.

## Auth and account
| Endpoint | Who | Purpose |
|---|---|---|
| POST /api/auth/login | public | `{username,password}` -> token, role. 5 failures lock the username for 15 min. |
| POST /api/auth/register | public | Beneficiary self-registration |
| GET /api/users/me | any | Profile incl. warehouseId / fpsId |
| POST /api/users/me/password | any | `{currentPassword,newPassword}` -> 204 |
| GET, POST /api/admin/users | admin | List / create staff accounts |
| GET, POST /api/admin/warehouses, /api/admin/fps | admin | Master data |

## Cards, beneficiaries, entitlement (admin, officer)
| Endpoint | Purpose |
|---|---|
| POST, GET /api/officer/ration-cards ; GET, PUT /{id} | CRUD; responses include `monthlyEntitlement` |
| PATCH /api/officer/ration-cards/{id}/eligibility?eligible= | Activate / deactivate |
| POST, GET, DELETE /api/officer/beneficiaries | Members; blocks duplicates and over-capacity cards |
| GET /api/officer/fps | Shop picker |

## Stock
| Endpoint | Roles |
|---|---|
| GET /api/warehouse/{id}/stock, /transactions, /shops | admin, officer, auditor, warehouse manager (own) |
| POST /api/warehouse/{id}/receipts, /damage, /transfers | admin, warehouse manager (own) |
| GET /api/fps/{id}/stock, /transactions | admin, officer, auditor, dealer (own) |
| POST /api/fps/{id}/damage | admin, dealer (own) |

## Distribution
| Endpoint | Roles |
|---|---|
| GET /api/fps/{id}/cards/{cardNo}/balance | admin, dealer (own) |
| POST /api/fps/{id}/distributions `{cardNumber, items:[{grainType, quantityKg}]}` | admin, dealer (own) -> receipt |
| GET /api/fps/{id}/distributions | admin, officer, auditor, dealer (own) |

## Beneficiary portal (BENEFICIARY)
GET /api/beneficiary/me, /entitlement, /distributions, /shops, /complaints ; POST /api/beneficiary/complaints

## Officer (admin, officer)
| Endpoint | Purpose |
|---|---|
| GET /api/officer/dashboard/summary, stock-by-grain, trend, shops, districts, top-shops | Dashboard data |
| GET, PATCH /api/officer/complaints | Triage queue |
| GET /api/officer/reports/monthly, districts, shops, complaints (+ `.csv`) | Reports. `?months=`, `?period=2026-10`. Auditors: `/api/audit/reports/...` |

## AI
| Endpoint | Purpose |
|---|---|
| POST /api/officer/ai/run | Run all checks now (also nightly 02:00 IST) |
| GET /api/officer/ai/alerts?status= ; PATCH /{id} | Alerts and review. Auditors: GET /api/audit/ai/alerts |
| GET /api/officer/ai/stock-forecast?type=FPS or WAREHOUSE | Days until stock runs out |
| GET /api/officer/ai/demand | Next-month demand and shortfall per shop and grain |
| POST /api/officer/ai/classify | `{text}` -> category, priority, department |
| GET /api/officer/ai/model-info | Complaint model size and cross-validation accuracy |
| GET /api/officer/ai/evaluation | Precision / recall of the fraud detectors on labelled demo anomalies |
| POST /api/admin/ai/demo-history?fpsId= | Synthetic history + injected anomalies (disable in production) |

## Assistant (Spring AI + RAG)
| Endpoint | Who | Purpose |
|---|---|---|
| POST /api/assistant/ask `{question}` | any signed-in user | -> `{answer, mode (LLM/LOCAL), sources[], liveData[], notice}`. 20 per minute per user. |
| GET /api/assistant/status | any signed-in user | Configured mode, retrieval type (VECTOR/KEYWORD), vector store state |
| POST /api/admin/assistant/reindex | admin | Re-embed the knowledge base |

Health check: GET /actuator/health
