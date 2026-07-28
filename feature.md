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

### GPX Ride Import
- **Status**: Implemented
- **Overview**: The ride history imports standard GPX 1.0/1.1 tracks as permanently stored rides and opens them in the existing detail review.
- **Compatibility**: LeanAngleTracker speed and lean extensions round-trip through the shared GPX codec; missing speed is derived from track distance and time where possible.
- **Safety**: Parsing is streaming and bounded to 250,000 track points, while Room stores each imported ride atomically.

### Track Review Quick Navigation
- **Status**: Implemented
- **Overview**: A full-track scrubber jumps directly to any recorded point while keeping the complete route stable on the map.
- **Detail navigation**: Dragging either detail graph returns to a centered map view and smoothly restores the last detail zoom (default: 17).

### Interactive App Tour
- **Status**: Implemented
- **Flow**: The gauge demonstrates a zero-to-right-to-left measurement before requiring a long-press reset. Recording combines start, the real rainbow GPS-waiting state, pause, resume, and stop/save in one guided sequence.
- **Ride details**: The history preview includes V-max and lets the user tap both V-max and max lean to preview jumping to important route points.
- **Navigation**: The tour has five pages. Next remains disabled on interactive pages until the demonstrated actions are completed; Skip is always available.

### Premium Features and Play Billing
- **Status**: Implemented
- **Products**: `auto_resume_unlock` is a permanent one-time Auto Resume unlock; `premium_subscription` is the subscription that unlocks all premium features and removes ads.
- **UI**: The Premium screen presents one-time purchases first and the subscription in a separate section. Purchase buttons use the localized price returned by Google Play.
- **Entitlements**: A subscription grants Auto Resume, ad-free use, and future premium features. An expired subscription does not remove a separately purchased Auto Resume unlock.
- **Configuration**: Both product IDs must exist and be active in Play Console. The subscription also needs an active base plan before prices and the purchase flow are available.
