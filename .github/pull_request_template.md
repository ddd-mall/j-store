## Intent

- Requirement / issue:
- Accepted delta, if behavior changes:
- In scope / out of scope:
- Target / source branch:

## Branch policy

- [ ] PR title follows Conventional Commits, for example `feat(order): reserve inventory`
- [ ] Daily work targets `develop`; only `release/v*` or `hotfix/v*` targets `master`
- [ ] The branch is rebased or merged with the latest target branch and contains no unrelated changes
- [ ] A release/hotfix includes a follow-up `master -> develop` synchronization plan

## Evidence

- [ ] Focused tests were run
- [ ] `./scripts/quality-gate.sh` passed, or skipped checks are explained below
- [ ] Public API/event/schema compatibility was considered
- [ ] Migration and rollback/forward recovery were considered when applicable
- [ ] No credential, production data, or real `.env` was added

Commands and results:

## Independent review

- Product/requirement drift findings:
- Quality/security findings:
- Required human approval:
- Merge method (`squash` for daily work; `merge` for release/hotfix/sync):

## Residual risk

Skipped checks, known limitations, rollout and observation notes:
