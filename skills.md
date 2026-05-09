# Skills & Procedures - LeanAngleTracker

This document preserves domain-specific knowledge and procedures to ensure development remains efficient and prevents "re-learning" costs.

## 1. Sensor Fusion & Math
- **Coordinate System**: The app assumes the phone is mounted in a specific orientation (usually portrait, upright). Calibration aligns the "Up" and "Forward" axes of the bike with the phone's internal sensors.
- **Vec3 Operations**: Use the `Vec3` class in `util/` for all vector math (dot product, cross product, normalization). Do not re-implement these manually.
- **Lean Angle Calculation**: Based on gravity direction and gyroscope integration.
  - *Procedure*: If changing math, use `grep "updateLeanAngle"` to find the entry point. Always verify with `fusionConfidence`.

## 2. UI State Management
- **Pattern**: `MainViewModel` exposes a single `StateFlow<UiState>`.
- **Granularity**: `UiState` contains nested data classes (`CalibrationUiState`, `TrackingUiState`, etc.).
- **Procedure**: When adding a new field, add it to the appropriate sub-state, not the root `UiState` if possible.

## 3. Storage & Persistence
- **GSON**: Current rides are stored as JSON files.
- **Procedure**: To add a field to a ride, update `TrackPoint` or `RideSession`. Note that old files might not have the new field; always provide default values.

## 4. Debugging & Verification
- **Log Tags**: Use `MainViewModel.TAG` ("MainViewModel").
- **UI Testing**: Use Compose Previews to verify UI changes without running the full app on a device.
- **Sensor Simulation**: (Future) Use a mock sensor provider for testing fusion logic.
