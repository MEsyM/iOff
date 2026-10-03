# Lone Rider shared content core

This directory is the single source of truth for game content shared by Android, iOS and CarPlay.

## Design
Platform UI and session logic remain native. Questions, people, words, categories and game configuration live here. Clients fetch the manifest, cache the last known-good version, validate schemaVersion and update atomically. Existing bundled banks remain an offline fallback until migration is complete.

This separation lets Android and iOS evolve on parallel branches without touching the same UI/game-engine files.

## First live endpoint
After merge, the repository can immediately serve the content from:

https://raw.githubusercontent.com/MEsyM/iOff/main/shared/content/v1/manifest.json

For development, replace main with the feature branch name.

## Production editing
The JSON contract is intentionally backend-agnostic. A future authenticated web admin can publish via the API shape in api/openapi.yaml while apps remain read-only. Publishing should increment contentVersion; clients refresh in the background and fall back to cache/offline content if the network or new content is invalid.

Do not store player profiles, XP, history or personal data in these files.
