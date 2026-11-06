# Lone Rider MCP development loop

This repository uses an agent-driven development loop with GitHub, Railway and browser automation as verification surfaces.

## Standard loop

1. Create a feature branch from the latest Lone Rider integration branch.
2. Implement the requested change.
3. Use GitHub MCP to create/update the branch and PR and inspect CI.
4. The Lone Rider deep test workflow must pass:
   - backend API integration against isolated PostgreSQL;
   - Android compile + lint;
   - Android emulator E2E user journeys;
   - screenshots at important checkpoints;
   - failure screenshot, UI hierarchy and logcat on emulator failure.
5. Deploy with Railway MCP only after CI is green.
6. Poll Railway until terminal SUCCESS; queued/building is never reported as live.
7. Use browser automation for post-deploy admin/web smoke checks when the feature has a web surface.
8. Report PR, CI result, deploy result and test artifacts.

## Required evidence before saying done

- Build successful.
- Backend deep tests green.
- Android lint green.
- Emulator E2E green.
- Screenshot artifact exists.
- Backend changes: Railway deployment observed as SUCCESS.
- Admin/web changes: real browser smoke check against deployed URL.
- APK changes: package identity/signature verification.

## Rule for future features

Every feature PR should add or extend at least one deep-test path. UI changes should add a screenshot checkpoint. Backend behavior should add an API assertion. Bugs should receive a regression step that fails before the fix and passes after it.

Production is never used as the CI test database; backend tests use an isolated PostgreSQL container.
