# LeanAngleTracker - Project Context for AI

This document provides a high-level overview of the LeanAngleTracker project to help AI assistants understand the codebase and assist with development tasks.

## Project Overview
LeanAngleTracker is an Android application designed for motorcyclists to track and record their lean angles during rides. It uses the device's inertial sensors (accelerometer, gyroscope, gravity) and GPS to calculate lean angles, speed, and routes.

## Core Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **State Management**: `StateFlow` within `MainViewModel`
- **Dependency Injection**: Manual/Simple (ViewModel with Application context)
- **Persistence**: Internal storage (Files) using GSON and Base64 obfuscation
- **Maps**: osmdroid
- **Navigation**: Custom route-based navigation (see `AppRoute.kt` and `MainActivity.kt`)

## Key Components

### 1. `MainActivity.kt`
The single activity of the app. It manages:
- Navigation state via `RouteUiState`.
- Permission requests.
- Foreground service lifecycle for tracking.
- Screen transitions using `AnimatedContent`.

### 2. `MainViewModel.kt`
The "brain" of the app. It handles:
- **Sensor Fusion**: Combines accelerometer, gyroscope, and gravity data to estimate lean angle reliably.
- **Calibration**: A multi-step process (`UPRIGHT`, `LEFT`, `RIGHT`) to define the bike's coordinate system relative to the phone's orientation.
- **Tracking Logic**: Manages active sessions, GPS updates, and recording samples.
- **UI State**: Exposes a unified `UiState` containing calibration, tracking, settings, and history data.

### 3. `TrackingService.kt`
A foreground service that keeps the tracking logic alive when the app is in the background. It ensures continuous data collection.

### 4. `data/RideRepository.kt`
Handles saving and loading ride sessions. Sessions are stored as Base64-encoded JSON files in the internal storage directory `internal_tracks`.

### 5. `ui/` Package Structure
- `calibration/`: UI for the sensor calibration process.
- `tracking/`: Main tracking screen, ride history, and ride details.
- `navigation/`: Definition of `AppRoute` and navigation utilities.
- `animation/`: Custom animations (e.g., bike lean visualization).
- `theme/`: Material 3 theme definitions.

## Key Data Models
- `RideSession`: Contains the start/end time, a list of `TrackPoint`s, and metadata like name and route description.
- `TrackPoint`: Individual data sample including timestamp, GPS coordinates, speed, and lean angle.
- `UiState`: Sealed or data classes representing the state of different screens.

## Calibration Process
Since a phone can be mounted in any orientation, the app requires calibration:
1. **Upright**: Capture gravity vector when the bike is vertical.
2. **Left/Right**: Capture tilt peaks to determine the forward/side axes.
This data is persisted in `SharedPreferences` to avoid re-calibration.

## Implementation Details
- **Sensor Processing**: High-frequency sensor updates are processed in `onSensorChanged`.
- **Fused Sample Recording**: Samples are recorded at a configurable interval (default 200ms) and saved into the `ridePoints` list.
- **Route Description**: Uses `Geocoder` or similar logic (see `calculateRouteDescription`) to name rides based on location.
