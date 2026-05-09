# Feature Requests & Backlog - LeanAngleTracker

This file tracks planned features and improvements. When developing a feature, review the "Context/Constraints" to avoid breaking existing functionality.

## Upcoming Features

### 1. Room DB Integration
- **Status**: Proposed
- **Description**: Replace file-based GSON storage with a Room database for better performance and query capabilities.
- **Constraints**: Must maintain backward compatibility for importing old file-based rides.

### 2. Auto-Pause Refinement
- **Status**: In-Progress
- **Description**: Improve the sensitivity of auto-pause.
- **Context**: Currently uses a lean threshold and speed threshold.

### 3. Modularization
- **Status**: Proposed
- **Description**: Split `app` module into `core`, `domain`, `data`, and `ui` modules.
- **Goal**: Enforce architecture boundaries and improve build times.

## Refactoring Backlog (SOLID/Clean Code)
- [ ] **Extract Sensor Logic**: Move `onSensorChanged` and fusion math from `MainViewModel` to a dedicated `SensorFusionManager`.
- [ ] **Extract Location Logic**: Move `onLocationChanged` and GPS tracking to a `LocationManager`.
- [ ] **State Splitting**: Break down `UiState` into smaller, screen-specific states to reduce recomposition.
- [ ] **Dependency Injection**: Introduce a DI framework (e.g., Hilt) to replace manual injection.

## Feature History (For Context)
*None yet recorded.*
