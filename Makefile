# Convenience wrapper (Linux/macOS). On Windows use the equivalent `pnpm <script>` commands.
.PHONY: env up obs stack down ps test java-test node-test py-test build

env:        ; pnpm env:init
up:         ; pnpm infra:up
obs:        ; pnpm obs:up
stack:      ; pnpm stack:up
down:       ; pnpm stack:down
ps:         ; pnpm stack:ps
java-test:  ; mvn -B verify
node-test:  ; pnpm -r test
py-test:    ; cd libs/python/swiftdrop_common && uv run pytest && cd ../../../services/ai-assistant-service && uv run pytest
test: java-test node-test py-test
build:      ; mvn -B -DskipTests package && pnpm -r build
