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

### Premium Features and Play Billing
- **Status**: Implemented
- **Products**: `auto_resume_unlock` is a permanent one-time Auto Resume unlock; `premium_subscription` is the subscription that unlocks all premium features and removes ads.
- **UI**: The Premium screen presents one-time purchases first and the subscription in a separate section. Purchase buttons use the localized price returned by Google Play.
- **Entitlements**: A subscription grants Auto Resume, ad-free use, and future premium features. An expired subscription does not remove a separately purchased Auto Resume unlock.
- **Configuration**: Both product IDs must exist and be active in Play Console. The subscription also needs an active base plan before prices and the purchase flow are available.
