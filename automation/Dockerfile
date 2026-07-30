# Runs the automation suite against an already-running stack (docker compose up --build
# at the repo root). This image does NOT bundle the application - it's the test runner
# only, meant to be pointed at APP_BASE_URL/API_BASE_URL for whatever environment is
# under test (local compose network, a staging host, etc).
FROM mcr.microsoft.com/playwright:v1.48.0-jammy

WORKDIR /automation

COPY package.json package-lock.json* ./
RUN npm ci

COPY . .
RUN npx tsc --noEmit

ENV CI=true
ENTRYPOINT ["npx", "playwright", "test"]
