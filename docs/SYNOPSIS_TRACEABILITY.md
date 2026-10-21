# Synopsis -> implementation

| Synopsis requirement | Where it is implemented |
|---|---|
| Six role-based portals, JWT + Spring Security | `security`, `auth`, `AccessGuard`; frontend `routes.jsx` |
| Beneficiary management, ration cards, automatic entitlement | `card`, `entitlement`; Cards page |
| Transaction-based auditable stock (Opening + Received - Distributed - Damaged) | `stock`, `StockTransaction` ledger; Warehouse and Shop Stock pages |
| FPS distribution: validate card, entitlement, prior distributions, stock, receipt | `distribution`; Distribute Grain page |
| Complaint handling with AI classification, priority, department | `complaint` (rules + Weka); My Complaints, Complaints pages |
| AI demand prediction (history, beneficiary counts, seasonality) | `ai/DemandModel`, `DemandPredictionService`; AI Forecasts page |
| Fraud / anomaly detection (abnormal quantities, repeated distributions) | `ai/IsolationForest`, `AnomalyDetectionService`; AI Alerts page |
| Stock exhaustion forecasting | `ai/StockForecastService`; AI Forecasts page |
| Role-based analytics: monthly, district-wise, shop performance (Chart.js) | `dashboard`, `reports`; Dashboard and Reports pages |
| React.js + Bootstrap SPA, responsive | `frontend/` |
| Spring Data JPA, Hibernate, MySQL, Maven, Java 21 | `backend/` |
| Spring AI, RAG-based AI services (updated requirements) | `assistant/`: Spring AI `ChatClient`, `EmbeddingModel`, `VectorStore` (SimpleVectorStore) over policy notes, plus role-scoped live data. Assistant page in the frontend. |
| AI/ML: Java, Spring Boot | Weka (complaints) + custom Isolation Forest + demand and stock-exhaustion models. DL4J and Tribuo are not used: the data volumes do not justify deep learning. |
| Git/GitHub, Docker | `docker-compose.yml`, Dockerfiles |
| Duplicate beneficiaries | Exact duplicate guard (name + Aadhaar last 4). Fuzzy matching is future work. |
