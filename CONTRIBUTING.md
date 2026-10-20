# Contributing to SwiftDrop

These rules apply to every team member. CI enforces the ones marked **(enforced)**.

## 1. No emojis **(enforced)**

Emojis and pictograph symbols are not allowed anywhere in the repository:

- source code, comments, log messages and UI text
- documentation, diagrams and config files
- commit messages, branch names, pull request titles/descriptions, issues and review comments

Use plain words instead (for example "OK", "Error", "Done", "Note:").
Check locally before pushing:

```bash
python scripts/check-no-emoji.py                         # all tracked files
git log --format=%B origin/main..HEAD | python scripts/check-no-emoji.py --stdin   # your commit messages
```

The `no-emoji` GitHub Actions check runs on every pull request (files, commit messages, PR title and
description) and must pass before merging.

## 2. Protected `main` branch **(enforced)**

- Nobody pushes directly to `main`; all changes go through a pull request.
- A pull request needs **1 approving review**, all conversations resolved and a passing `no-emoji` check.
- New commits dismiss earlier approvals; force pushes and branch deletion are blocked.

## 3. Branches and commits

- One branch per feature or fix: `feat/m<N>-<topic>`, `fix/m<N>-<topic>` (for example `feat/m4-proof-upload`).
- Conventional Commits: `feat(parcel-service): add status state machine`, `fix(web): ...`, `test: ...`, `docs: ...`.
- Keep pull requests small and focused on one issue; link it with `Closes #<issue>`.

## 4. Definition of Done

Code, unit and integration tests (coverage at least 70 %), authorization rules tested, API/event contracts
updated in `contracts/`, Dockerfile/compose/Helm values updated, no secrets or personal data in code or logs,
diagrams for your feature in `doc/diagrams`, and one approving review.
