# GymManager

GymManager is an offline-first gym management app built with Kotlin Multiplatform and Compose Multiplatform. It is designed for gym owners and managers who want to track members, memberships, dues, renewals, and reminders from a single app without needing a cloud backend.

## What this app does

GymManager helps manage the day-to-day operations of a gym:

- Track member profiles, contact details, notes, photos, and membership history
- Manage gym plans and memberships
- Record payments and monitor dues and outstanding balances
- Renew memberships in bulk for multiple members at once
- View dashboards for active, expiring-soon, expired, and due members
- Import member records from CSV files
- Send reminder flows for renewals and follow-ups
- Configure gym details, themes, and app language
- Run on both Android and iOS using a shared codebase

## Key features

### Member management
- Add, edit, archive, and review member records
- Store basic personal details, date of birth, notes, and photos
- Open a dedicated member detail screen for deeper membership and billing information

### Membership and billing
- Create and manage membership plans
- Sell memberships to members
- Track collected revenue and unpaid dues
- Use shared business rules to validate membership and payment logic

### Dashboard and operations
- See totals for active members, due members, and expiring memberships
- Review upcoming renewals and overdue balances in one place
- Use bulk renewal actions to process many members efficiently
- Import member data in batches to speed up onboarding

### Communication and reminders
- Prepare WhatsApp-style reminder messages for members
- Trigger follow-up reminders for payment and renewal needs
- Keep communication tied to member and membership records

### Offline-first architecture
- Uses a local Room database for persistence
- Keeps core logic in shared code for Android and iOS consistency
- Designed to work without requiring an online backend

## Tech stack

- Kotlin Multiplatform
- Compose Multiplatform
- Android and iOS targets
- Room for local data persistence
- DataStore for settings
- Kotlinx DateTime

## Project structure

- `androidApp/` — Android app entry point
- `iosApp/` — iOS app entry point
- `sharedLogic/` — business logic, repositories, models, and local database code
- `sharedUI/` — shared UI screens, Compose components, navigation, and app theme

## Running the app

### Android
```bash
./gradlew :androidApp:assembleDebug
```

### iOS
Open the `iosApp` folder in Xcode and run the project from there.

## Running tests

```bash
./gradlew :sharedUI:testAndroidHostTest :sharedLogic:testAndroidHostTest
./gradlew :sharedLogic:iosSimulatorArm64Test
```

## Notes

This project is intended as a complete local gym management solution for offline use, with a shared cross-platform codebase focused on real-world gym operations.
