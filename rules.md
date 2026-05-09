# Development Rules - LeanAngleTracker

These rules are designed to make development cost-effective, minimize errors, and ensure long-term maintainability.

## 1. Cost & Efficiency Rules
- **Token Conservation**: Before reading a 1000-line file, use `grep` or `resolve_symbol` to target the specific logic needed.
- **Incremental Refactoring**: Never perform a "pure refactor" task without a clear functional goal. Combine small refactors with feature work (The Boy Scout Rule).
- **No Redundant Searches**: Maintain `skills.md` so complex logic doesn't need to be "re-discovered" across sessions.

## 2. Code Integrity Rules
- **The "God Class" Lock**: `MainViewModel.kt` is locked for new logic. Any new feature must be implemented in a UseCase or Manager and then injected/called.
- **Sensor Safety**: Any change to `onSensorChanged` or math in `Vec3` must be verified against current physical logic. Do not guess math formulas.
- **State Immutability**: UI state must always be updated via `.update { it.copy(...) }` to ensure thread safety and predictable recomposition.

## 3. Communication Rules
- **Pre-flight Check**: Before writing code, summarize the planned changes in 3-5 bullet points for the user.
- **Mini-Step Validation**: After a significant logic change (especially math/sensors), ask the user to verify or run a build before proceeding to UI styling.
- **Document Changes**: If a new pattern is introduced, update `architecture.md` or `skills.md` immediately.

## 4. Compose Rules
- **No Business Logic in UI**: Composables should only handle display and event emission. No calculations allowed in `@Composable` functions.
- **Preview First**: Create or update `@Preview` for every modified UI component to ensure visual regression is caught early.
