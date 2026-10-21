# GrainGuard AI - AI-Powered Public Grain Distribution System (PGDS)

Final-year B.Tech (CSE) project. Java 21 + Spring Boot 3.5 + Spring AI 1.1 + MySQL + React + Weka / custom Isolation Forest.

    pgds/
      backend/    Spring Boot REST API, JWT + RBAC, ledger-based stock, AI service
      frontend/   React 18 + Vite + Bootstrap + Chart.js
      docs/       architecture, API reference, user guide, testing and AI evaluation
      docker-compose.yml, .env.example

## Quick start (Docker)
    cp .env.example .env        # edit the passwords and JWT_SECRET
    docker compose up --build
Open http://localhost:3000. Demo accounts (password `Pgds@12345`): admin, officer, warehouse, dealer, auditor, beneficiary.
Change these passwords (My account) or set `SEED_DEMO=false` before exposing the system anywhere.

## Development without Docker
    docker run -d --name pgds-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=pgds -p 3306:3306 mysql:8.4
    cd backend  && mvn test && mvn spring-boot:run      # http://localhost:8080
    cd frontend && npm install && npm run dev           # http://localhost:3000

## Demo script (5 minutes)
1. admin: Users & Tools -> Generate demo history (60 cards, 5 months of data, 34 injected anomalies).
2. admin or officer: AI Alerts -> Run analysis -> Evaluate detectors (precision / recall vs the injected anomalies).
3. officer: Dashboard, AI Forecasts, Reports (download a CSV).
4. officer: Ration Cards -> edit MH-PUN-000001 and assign it to FPS101; dealer: Distribute Grain -> issue and print a receipt.
5. beneficiary: My Ration shows the remaining entitlement; submit a complaint and see the AI category.

## AI assistant
Works immediately in `local` mode (no account). For natural-language answers set `ASSISTANT_MODE=llm` plus an OpenAI key, or use free local Ollama.
See `docs/ASSISTANT_RAG.md`.

## Production checklist
- `SPRING_PROFILES_ACTIVE=prod` (refuses to start with the default JWT secret, demo data, or the demo endpoint)
- Random `JWT_SECRET`, strong DB passwords, `SEED_DEMO=false`, `AI_DEMO_ENDPOINT=false`
- Serve behind HTTPS (reverse proxy) and set `PUBLIC_API_URL` / `CORS_ORIGIN` to the real URLs
- Database schema is managed by Hibernate `ddl-auto: update`; adopt Flyway migrations before real data
- Login lockout is in-memory (per instance); use a shared store if you run several backend instances
- Back up the MySQL volume; the stock ledger is the audit trail

See `docs/` for details.
