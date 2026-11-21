# M4 Courier Ops & Status Lifecycle diagrams (Issue #17)

Sources are Mermaid (`.mmd`); the PNG and SVG next to each are exported with `@mermaid-js/mermaid-cli`.

| Diagram | Shows |
|---|---|
| [`state-m4-parcel-lifecycle`](state-m4-parcel-lifecycle.png) | Allowed status transitions, who may make each one, and what each one requires (from `ParcelStates.java`) |
| [`usecase-m4-courier`](usecase-m4-courier.png) | What couriers and dispatchers can do in courier operations |
| [`sequence-m4-status-update`](sequence-m4-status-update.png) | A courier marking a parcel delivered with a photo proof, the single transaction with the outbox, and the fan-out over Kafka |

To re-export: `pnpm exec mmdc -i <file>.mmd -o <file>.png -b white -s 2` (and `-o <file>.svg`).
