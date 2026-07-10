# Coding Guidelines - TiloCompose

> **For AI agents:** Before starting any work, also read [`AGENT_SKILLS.md`](./AGENT_SKILLS.md) — it contains instructions specific to automated tools (existing types in `core`, CRS rules, Compose cache rules, language rules).

This document defines coding standards and best practices for the TiloCompose project (Kotlin Multiplatform + Compose). It is intended for contributors and automated tools (linters, CI).

## Goals and Scope
- Ensure consistent, readable and safe code across all platforms.
- Minimize bugs caused by incorrect usage patterns in Compose, coroutines and multiplatform code.
- Make code review easier and support automated checks (ktlint, detekt, CI).
- Cover Kotlin, Jetpack Compose, Coroutines, Kotlin Multiplatform, Android/iOS specifics, testing and CI.

This repository hosts a multiplatform library for working with maps: tile layers, vector features, drawing, and a plugin system. Public API design and binary compatibility should be considered when changing public symbols.

## Project structure and naming
- Public library packages: use the `tilo.compose` root package (`tilo.compose.core`, `tilo.compose.render`, `tilo.compose.draw`). The showcase application may keep its Android application package under `eu.tilo.compose`. Use lowercase package names without underscores.
- Files: prefer one public top-level class/interface per file. Exceptions: small helpers or tightly related Composables for the same screen/component.
- File names: PascalCase for primary classes/components (`MapView.kt`, `TileLayer.kt`).

## Kotlin conventions
- Naming: classes/objects PascalCase, functions and variables camelCase, constants UPPER_SNAKE_CASE.
- Visibility: prefer the narrowest visibility (private/internal). Declare visibility explicitly for important API surface.
- Data classes: use `data class` for immutable models; avoid mutable constructor properties.
- Sealed classes: use for exhaustive state modeling where appropriate.
- Null-safety: prefer non-null types; if nullable, document the reason and contract.
- Extension functions: use sparingly and only when they provide a clear API benefit.

## Public API and binary stability
- Public API in `commonMain` should be documented with KDoc.
- Maintain backward compatibility for stable public APIs. If an API-breaking change is necessary, increase the major version and document migration steps.

## Compose conventions
- State hoisting: Composables should be stateless when possible. Parent components or ViewModels own the state.
- Single source of truth: manage state centrally in a ViewModel or a parent composable; expose StateFlow/MutableState as appropriate.
- Recomposition: avoid heavy computations inside @Composable functions. Use `remember` for expensive derived values.
- remember: use for caching derived data; provide keys if value depends on parameters.
- Keys: use `key` in Lazy lists and when preserving identity matters.
- Side-effects: use `LaunchedEffect`, `DisposableEffect`, and other side-effect APIs for IO, lifecycle interactions or one-off actions.
- Modifiers: chain modifiers and keep UI logic declarative.

## Coroutines and concurrency
- Structured concurrency: use lifecycle-aware scopes (ViewModelScope, lifecycleScope). Do not use GlobalScope.
- Dispatchers: use IO for I/O-bound, Default for CPU-bound, Main for UI. Prefer passing dispatcher as a parameter for testability.
- Error handling: handle exceptions in coroutines with try/catch or a CoroutineExceptionHandler where appropriate. Surface errors to UI in a user-friendly way.
- Cancellation: code should cooperate with cancellation (use cancellable APIs, check isActive if needed).

## Kotlin Multiplatform
- Separation: `commonMain` contains business logic without platform-specific APIs. Use `expect/actual` for platform bindings.
- Serialization: prefer `kotlinx.serialization` for shared models.
- Resources: shared Compose resources go into `commonMain/composeResources`.

## Android specifics
- Activity/Compose: Activity should be a thin host. Keep business logic in ViewModels.
- Lifecycle: use lifecycle-aware constructs and `repeatOnLifecycle` for collecting flows.
- Resources: keep strings in `res/values/strings.xml`. Avoid hardcoded strings in the UI.

## iOS specifics
- Interop: use `kotlinx.serialization` for model interchange with Swift.
- Keep platform-specific code minimal and wrapped behind `expect/actual` or small adapters.

## Testing
- Unit tests: write tests for shared logic in `commonTest` and platform-specific tests in the respective source sets.
- UI tests: use Compose testing framework for Android where applicable.
- When changing behavior, add or update tests.

## CI and linters
- Mandatory CI checks: ktlint (format/style) and detekt (static analysis).
- The project CI runs check-only tasks (no instrumentation builds), for broader safety on CI environments.
- Locally: run `./gradlew ktlintFormat` before committing.

## Commits and PRs
- Commit messages: use a short prefix like `[feat|fix|chore|docs|refactor|test]` then a concise message. Examples: `feat: add vector tile parser`.
- PR checklist: describe the change, include screenshots for UI changes, link the issue if any, ensure tests are added/updated, and CI passes.

## Secrets and security
- Never commit secrets (API keys, certificates). Use CI-managed secrets and environment variables.
- If secrets were accidentally committed, rotate them and purge history.

## Accessibility and performance
- Accessibility: provide contentDescription for images, proper roles and focus order where applicable.
- Performance: profile recompositions, prefer Lazy lists for large data sets and avoid unnecessary object allocations in hot paths.

## Documentation
- Document public APIs with KDoc and provide short usage examples in `README.md` or `docs/`.

## Shared types in `core` — use, don't duplicate

The `core` module contains canonical types shared across all modules. **Always check `core` before creating a new local type.**

| Type | Package | Use instead of |
|---|---|---|
| `BoundingBox` | `tilo.compose.core.geometry` | any local `WorldBounds`, `Rect`, `Bounds` data class |
| `Point` | `tilo.compose.core.geometry` | local `Vec2`, `Coordinate`, `LatLon` wrappers |
| `Geometry` + subtypes | `tilo.compose.core.geometry` | any local geometry representations |
| `TileCoordinate` / `TileBounds` | `tilo.compose.core.tile` | local tile address structs |
| `Viewport` | `tilo.compose.core.map` | local screen-size holders |

Rules:
- If you need a new property or method on a shared type (e.g. `BoundingBox.intersects`), **add it to `core`**, do not wrap or extend it locally.
- If a `core` type is missing something, extend it rather than creating a parallel type.
- Never import a `core` type and then shadow it with a same-concept local type in the same module.

---

Rules for the assistant (bot) when editing this repository
1. Do not add new dependencies without justification in the PR.
2. Add or update tests when changing behavior.
3. Do not rewrite existing code without an explanation in the PR.
4. Follow naming conventions and package structure.
5. Use Compose side-effect APIs (`remember`, `LaunchedEffect`) — do not perform IO directly in composables.
6. Never use GlobalScope — always use structured concurrency.
7. Add KDoc for new public symbols.
8. Run `./gradlew ktlintFormat` locally before committing.
9. Prefer minimal visibility; use `internal`/`private` where possible.
10. Do not add secrets to the repo; use environment variables in CI.
11. When refactoring, preserve behavior or provide tests and migration notes.
