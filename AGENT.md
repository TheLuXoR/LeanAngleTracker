# LeanAngleTracker - Agent Context

This document is the "Brain" of the project's development. It defines how we work to keep development cost-effective and high-quality.

## Project Vision
A robust, high-performance motorcycle lean angle tracker using sensor fusion. We prioritize accuracy, battery efficiency, and a clean codebase through incremental SOLID improvements.

## Core Documentation
- [Development Rules](rules.md) - **CRITICAL**: Read this to minimize development costs.
- [Skills & Procedures](skills.md) - How to perform common tasks in this project.
- [Architecture](architecture.md) - Design patterns and migration path.
- [Code Style & SOLID](codestyle.md) - Standards for Kotlin and Compose.
- [Features & Backlog](feature.md) - Feature requests and context preservation.

## Fast Start Checklist (for any coding session)
1. Read `rules.md` and this file before editing code.
2. Search for existing helpers/use-cases before adding logic to `MainViewModel`.
3. If touching sensor math, keep changes tiny and behavior-preserving first.
4. Update docs in the same commit when architecture boundaries move.

## Current State & Tech Stack
- **Language**: Kotlin | **UI**: Jetpack Compose
- **Architecture**: MVVM -> Clean Architecture (Transitioning)
- **Sensors**: High-frequency fusion in `MainViewModel.kt` (Target for extraction).
- **Storage**: Room Database (with strict Migration rules).

## Critical Areas
- **MainViewModel.kt**: The "God Class" (currently ~1500 lines). **Rule: New logic goes into UseCases/Managers, not here.**
- **UiStateModels.kt**: Canonical home for UI-state and ride DTO data classes extracted from `MainViewModel`.
- **Sensor Math**: Precision is critical. Verify math against `Vec3.kt` and `util/` helpers.
- **Database Schema**: Version 3. Always preserve user rides via `Migration` objects.

## Agent Guidelines
1. **Incrementalism**: Small, verifiable steps. Never refactor a whole module in one go.
2. **Context Efficiency**: Check `rules.md` for how to minimize token usage and maximize precision.
3. **Documentation Sync**: Update `.md` docs *before* or *immediately after* code changes.
