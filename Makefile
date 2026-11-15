# Convenience wrapper (Linux/macOS). On Windows use the equivalent `pnpm <script>` commands.
.PHONY: env up obs stack down ps test java-test node-test py-test build helm-deps helm-lint kind-up kind-deploy kind-down

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

# --- Kubernetes / Helm (infra/helm/README.md has the full walkthrough) ---
helm-deps:   ; ./scripts/helm-deps.sh
helm-lint:   helm-deps
	helm lint infra/helm/swiftdrop infra/helm/charts/*
	helm template swiftdrop infra/helm/swiftdrop -f infra/helm/swiftdrop/values-dev.yaml >/dev/null
	helm template swiftdrop infra/helm/swiftdrop -f infra/helm/swiftdrop/values-prod.yaml >/dev/null
kind-up:     ; ./scripts/kind-up.sh
kind-deploy: ; ./scripts/kind-deploy.sh
kind-down:   ; kind delete cluster --name swiftdrop
