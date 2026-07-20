# Branching Strategy

Read by `/ccmagic:pr` and `/ccmagic:merge`.

## Strategy

Strategy: github-flow

Primary Branch: main

## Conventions

- `feature/{slug}` — new features
- `bugfix/{slug}` — bug fixes
- `hotfix/{slug}` — production hotfixes
- `chore/{slug}` — maintenance
- `docs/{slug}` — documentation
- `test/{slug}` — test-only changes
- `build/{slug}` — build/CI changes

## Merge strategy

- feature/bugfix/hotfix/chore → squash merge
- release/* → merge commit
