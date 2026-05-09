# Code Style & SOLID - LeanAngleTracker

## General Principles
- **Clarity over Brevity**: Name variables and functions clearly (e.g., `calculateLeanAngle` instead of `calcLA`).
- **Consistency**: Follow existing patterns (Compose for UI, StateFlow for state).
- **Kotlin Idioms**: Use `apply`, `also`, `let`, and extension functions where they improve readability.

## SOLID Guidelines
1. **Single Responsibility (SRP)**: A class should have one reason to change.
   - *Issue*: `MainViewModel` handles sensors, GPS, and UI state.
   - *Fix*: Extract sensor logic to a `SensorManager` and GPS logic to a `LocationProvider`.
2. **Open/Closed**: Classes should be open for extension but closed for modification.
3. **Liskov Substitution**: Use interfaces for repositories and data sources to allow swapping (e.g., File storage vs. Room).
4. **Interface Segregation**: Don't force a class to implement methods it doesn't use.
5. **Dependency Inversion**: High-level modules should not depend on low-level modules. Both should depend on abstractions.

## Handling "God Classes" (The 1000-Line Rule)
When a class exceeds 500-1000 lines (like `MainViewModel.kt`), any new change **must** include a mini-refactor:
1. **Identify a Responsibility**: Find a group of related functions (e.g., all `Calibration` logic).
2. **Extract to Use Case/Helper**: Create a new class (e.g., `CalibrationManager`) and move the logic there.
3. **Delegate**: Call the new class from the original class.
4. **Mini Steps**: Do not attempt a 100% refactor in one go. Extract one responsibility at a time.

## Compose Best Practices
- **Stateless Composables**: Pass state in, emit events out.
- **Hoisting**: Keep state as high as necessary, but no higher.
- **Previews**: Every UI component should have a `@Preview`.
