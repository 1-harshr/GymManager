# GymManager

**GymManager is an offline-first app for running a gym’s everyday membership operations.** Gym owners and staff can keep member records, manage plans and memberships, record payments, follow up on dues, and see upcoming expirations—all from one place. The app is built for Android and iOS, with shared Kotlin business logic and UI.

## Product overview

### Keep member information organized
Create and update member profiles with contact details, notes, photos, and membership history. Import member records from CSV to make onboarding easier.

### Manage plans, memberships, and payments
Set up gym plans, sell memberships, record payments, and review outstanding dues. Renew memberships individually or use bulk renewal for multiple members.

### Know what needs attention
The dashboard highlights active, expiring-soon, expired, and owing members, along with monthly collections and amounts due. Use these summaries to prioritize renewals and follow-ups.

### Follow up with members
The app provides WhatsApp-style message and reminder flows for membership renewals and payment follow-ups.

### Work offline
GymManager stores operational data locally, so its core gym-management workflows do not depend on a hosted backend or an active internet connection.

## Platforms and technology

- **Android and iOS** apps
- **Kotlin Multiplatform** for shared business logic
- **Compose Multiplatform** for shared UI
- **Room** for local database persistence
- **DataStore** for app settings

## Developer guide

### Repository layout

- `androidApp/` — Android application entry point and platform configuration
- `iosApp/` — Xcode project and iOS application entry point
- `sharedLogic/` — domain models, business rules, repositories, use cases, and local data layer
- `sharedUI/` — shared Compose screens, components, navigation, and theme

### Prerequisites

- Android Studio with Kotlin Multiplatform support for Android development
- Xcode on macOS to build and run the iOS app
- A JDK compatible with the project’s Gradle configuration

The repository includes the Gradle wrapper, so use `./gradlew` rather than installing Gradle separately.

### Build and run

Build the Android debug app:

```bash
./gradlew :androidApp:assembleDebug
```

To run on iOS, open the `iosApp` project in Xcode, select a simulator or device, and run it.

### Run tests

Run the Android host tests:

```bash
./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest
```

Run the iOS simulator tests:

```bash
./gradlew :sharedLogic:iosSimulatorArm64Test
```

## Project status

GymManager is an actively developed cross-platform project. Features and implementation details may evolve; check the source code and tests for the current behavior.
