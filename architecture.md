# Architecture - LeanAngleTracker

## Current State: Monolithic MVVM
The project currently follows a traditional MVVM pattern, but with a highly centralized `MainViewModel`.

- **View**: Jetpack Compose screens in the `ui/` package.
- **ViewModel**: `MainViewModel` handles almost all logic (Sensors, GPS, State, Persistence).
- **Model**: Data classes like `RideSession`, `TrackPoint`, and `UiState`.
- **Data Source**: `RideRepository` (File-based) and `SettingsStore` (SharedPreferences).

## Target State: Clean Architecture (Incremental Migration)
The goal is to move towards a decoupled architecture to improve testability and reduce class sizes.

### 1. Domain Layer (Pure Kotlin)
- **Entities**: Business objects (e.g., `RideSession`).
- **Use Cases**: Single-purpose interactors (e.g., `SaveRideUseCase`, `CalculateLeanAngleUseCase`).
  - *Current Status*: `RideSessionUseCases` exists but needs expansion.

### 2. Data Layer
- **Repositories**: Interfaces defined in Domain, implemented here.
- **Data Sources**: Room DB (future), File Storage, Sensor API, Location API.

### 3. Presentation Layer
- **ViewModels**: Should only handle UI state and delegate logic to Use Cases.
- **UI State Models**: Keep shared UI/ride state models in dedicated files (e.g., `UiStateModels.kt`) instead of inside giant viewmodels.
- **UI Components**: Purely visual, observing `StateFlow`.

## Package Structure
- `com.example.leanangletracker`
  - `data/`: Repositories, Data Sources, DTOs.
  - `domain/`: Use Cases, Entities (Domain Models).
  - `ui/`: Compose screens, ViewModels, UI State.
  - `util/`: Helper classes (Math, Vec3).
  - `sensor/`: (Proposed) Dedicated sensor fusion and management.
