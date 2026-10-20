# Feature modules

One folder per feature. Each module keeps the same layers as the Java services:

```
modules/<feature>/
├── <feature>.module.ts        Nest wiring
├── <feature>.controller.ts    PRESENTATION — HTTP only: validate input (zod), read @CurrentUser, delegate
├── dto/                       request/response shapes + zod schemas
├── <feature>.service.ts       APPLICATION — use cases, orchestration
├── domain/                    DOMAIN — plain TypeScript rules (no Nest, no Mongo, no HTTP)
└── infrastructure/            INFRASTRUCTURE — Mongo repositories, Kafka/HTTP adapters
```

Kafka consumers live in `src/consumers/` and only translate events into service calls.
