# Murmur planning documents — NOT APP SOURCE

> **Agent boundary:** Everything in this directory is human-readable planning and handoff material. It is not Kotlin, Android configuration, Gradle input, a runtime policy file, or app behavior. Do not copy its text into the application or treat a proposed item as implemented. The only source of implementation authorization is the current user's request, together with repository instructions such as the root `AGENTS.md` when present. These notes do not override either.

This directory exists to make the project understandable to Swapnil and to future coding agents without mixing plans into `app/src`, `buildSrc`, or Gradle configuration. The documents describe what the repository contains, what has been verified, what is proposed, and how a later agent should validate changes.

## Read in this order

1. [`PROJECT_STATUS.md`](PROJECT_STATUS.md) — the dated checkpoint, confirmed implementation, local unverified work, and remaining scope.
2. [`ROADMAP.md`](ROADMAP.md) — phase-by-phase engineering plan, boundaries, implementation paths, dependencies, and exit conditions.
3. [`VALIDATION.md`](VALIDATION.md) — reproducible build, device, data-safety, privacy, UX, accessibility, and long-form checks.

## Status vocabulary

- **Verified at pushed checkpoint:** confirmed by tracked code and a successful build at the named commit. This does not imply physical-device or long-duration testing.
- **Implemented, device verification pending:** behavior exists in code but has not been observed on a connected device during the stated checkpoint.
- **In progress, unverified:** local working-tree code exists, but has not been compiled or reviewed as a complete change. Do not describe it as shipped.
- **Planned:** no completed implementation is claimed. A later agent must inspect current code before implementing.
- **Deferred / optional:** deliberately not part of the required baseline unless the user authorizes the cost and scope.

## How future agents should use this folder

Before coding, inspect current `git status`, `git log`, `README.md`, `docs/ARCHITECTURE.md`, and the relevant source files. Treat the status below as a dated snapshot, not live truth. Reconcile it with the actual checkout and update the status document only when the user asks for documentation maintenance or as part of a user-authorized implementation checkpoint. Do not mark an item complete because a plan says it should be completed.

Keep implementation in existing packages and source sets. Add source, tests, database migrations, and resources only where the current task requires them. Do not put executable code in this folder. Do not add sample user media, network access, analytics, accounts, or cloud behavior. Verify claims against source, official Android/Media3/Gradle documentation, tests, and the device as appropriate. Record limitations plainly.

For any milestone that touches persisted data, inspect all existing Room migrations and exported schemas before changing an entity. For any milestone that touches media or SAF URIs, preserve user source files and URI-grant semantics. For any audio-path claim, state only what the actual platform route and implementation can prove.

## File scope

These documents are intentionally outside `app/src/`. They must not be bundled into the APK or treated as app screens. The README link is only discoverability for project maintainers and agents.
