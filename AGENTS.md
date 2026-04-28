# AGENTS.md

## Purpose

This repository is an Android/Kotlin extension for Hammerhead Karoo that computes and displays W' Balance metrics.

Use this file as the default operating guide for any agent working in this repo. Keep changes small, testable, and focused on the requested scope.

Primary product context lives in `README.md`.

## Project Snapshot

- **Language/runtime:** Kotlin + Android (Gradle Kotlin DSL)
- **Build system:** Gradle wrapper (`./gradlew`)
- **Java version:** 17
- **Main module:** `app`

## Repository Map

- App code: `app/src/main/kotlin`
- Android resources: `app/src/main/res`
- Manifest: `app/src/main/AndroidManifest.xml`
- Unit tests: `app/src/test/kotlin`
- Test resources: `app/src/test/resources`
- Module build config: `app/build.gradle.kts`
- Shared dependency versions: `gradle/libs.versions.toml`
- CI workflows: `.github/workflows/android.yml`, `.github/workflows/pre-release.yml`, `.github/workflows/release_notes.yml`

## Required Toolchain

- Use the Gradle wrapper provided by the repo (`./gradlew`).
- Use Java 17 for local builds/tests to match CI and module toolchain settings.
- Respect Kotlin official style (`kotlin.code.style=official` in `gradle.properties`).

## Commands To Run Before Hand-off

Run from repo root:

1. `./gradlew build`  
   Primary validation command and CI parity check (used in release/pre-release workflows).
2. `./gradlew test`  
   Fast verification for unit tests when touching logic.
3. Optional focused checks while iterating:
   - `./gradlew :app:test`
   - `./gradlew :app:assemble`

If a change affects build/release behavior, run `./gradlew :app:assemble` at least once locally.

## Testing Expectations

- Unit tests are configured to run on JUnit Platform (JUnit 5).
- For logic changes in `app/src/main/kotlin`, add/update tests in `app/src/test/kotlin` when feasible.
- Prefer deterministic tests; avoid hidden time/network/device dependencies in unit tests.
- If you cannot add tests, clearly explain why in hand-off notes and include manual verification steps.

## Release and CI Constraints

- Do not break existing workflow assumptions in:
  - `.github/workflows/android.yml`
  - `.github/workflows/pre-release.yml`
- Build workflows expect release APK output at:
  - `app/build/outputs/apk/release/app-release.apk`
- `assemble` depends on `generateManifest` in `app/build.gradle.kts`, which writes:
  - `app/manifest.json`
- Stable release tags are matched by `*[0-9]-release`; prerelease tags by `*pre-release`.

If you modify versioning, build tasks, or artifact paths, update workflows in the same change.

## Change Scope Rules

- Make the smallest change that solves the requested task.
- Do not refactor unrelated code "while here."
- Do not change dependency versions unless required by the task.
- Avoid moving files or renaming symbols without strong reason.
- Preserve existing public behavior unless the task explicitly changes behavior.

## Safety Rules

- Never commit secrets, tokens, keystores, or credentials.
- Do not modify ignored/generated artifacts unless explicitly requested:
  - `app/manifest.json`
  - `app/release/*`
  - `build/*`
- Avoid destructive git operations (`reset --hard`, force pushes, deleting branches) unless explicitly requested.
- Keep release automation intact unless the task is explicitly about release automation.

## Handoff Format

When finishing a task, provide:

1. **What changed**
   - File-by-file summary of edits.
2. **Why it changed**
   - Brief intent tied to the request.
3. **How it was validated**
   - Exact commands run and results.
4. **Risks / follow-ups**
   - Any known edge cases, trade-offs, or deferred work.

## Out of Scope By Default

- Repo-wide style rewrites.
- Dependency upgrades unrelated to the task.
- CI workflow redesign.
- Release process changes.

