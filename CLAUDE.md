# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Prerequisites
- **JDK 17** (Required - JDK 21 has KAPT compatibility issues)
- Android SDK 34
- Min SDK: 26 (Android 8.0)

### Common Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all checks (lint + tests)
./gradlew build

# Run lint only
./gradlew lintDebug

# Install debug APK on device
./gradlew installDebug
```

### Build Outputs
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Testing
Test framework is configured but tests need to be implemented. Test dependencies are already in place.

## Architecture Overview

This is a **Modern Android Email Client** built with Clean Architecture principles, MVVM pattern, and **Jetpack Compose** for UI.

### Core Architecture Layers

```
presentation/ (UI Layer - Compose Screens & ViewModels)
    ├── theme/              # Material3 theme (Color, Type, Theme)
    ├── navigation/         # Compose Navigation (Screen, NavGraph)
    ├── components/         # Reusable Compose components
    ├── setup/              # Account setup screens (WelcomeScreen, ManualConfigScreen)
    ├── inbox/              # Email list with selection mode (InboxScreen)
    ├── detail/             # Email viewer with HTML rendering (EmailDetailScreen)
    ├── compose/            # Email composition screen (ComposeEmailScreen)
    ├── folder/             # Folder view (FolderViewScreen)
    ├── folders/            # Folder management (FolderManagementScreen)
    ├── settings/           # App settings (SettingsScreen)
    └── MainActivity.kt     # Compose-based main activity

domain/ (Business Logic)
    ├── model/          # Domain models (Email, Account, Folder)
    └── repository/     # Repository interfaces

data/ (Data Layer)
    ├── local/
    │   ├── dao/        # Room DAOs
    │   ├── entities/   # Room entities
    │   ├── AppDatabase.kt
    │   ├── AppPreferences.kt
    │   └── CredentialManager.kt  # Encrypted credential storage
    ├── remote/
    │   ├── imap/       # IMAP email fetching
    │   └── smtp/       # SMTP email sending
    └── repository/     # Repository implementations

di/ (Dependency Injection)
    ├── DatabaseModule.kt
    ├── NetworkModule.kt
    └── RepositoryModule.kt
```

### Key Architectural Patterns

**UI Framework**: **Jetpack Compose** with Material3 components. All screens are composables using declarative UI patterns.

**Dependency Injection**: Uses Hilt/Dagger for all dependencies. ViewModels use `@HiltViewModel` and screens use `hiltViewModel()` for injection.

**Repository Pattern**: All data access goes through repository interfaces defined in `domain/repository/` and implemented in `data/repository/`. ViewModels depend only on repository interfaces, never DAOs or services directly.

**Database**: Room database with 3 main entities:
- `AccountEntity` - Email accounts with IMAP/SMTP configuration
- `EmailEntity` - Email messages with folder relationships
- `FolderEntity` - Folders (Inbox, Sent, Drafts, Custom)

Database migrations exist in `AppDatabase.kt`. Currently at version 3 with migrations for profile images and auto-download settings.

**State Management**: ViewModels expose `StateFlow` and `SharedFlow` for UI state. Compose screens use `collectAsStateWithLifecycle()` to observe state. Use `viewModelScope` for coroutines.

**Navigation**: Uses Compose Navigation with type-safe routes. Navigation graph is in `presentation/navigation/NavGraph.kt`. Routes are defined as sealed class in `Screen.kt`. MainActivity uses `EmailClientNavHost` composable for navigation.

### Email Protocol Implementation

**IMAP Service** (`data/remote/imap/IMAPService.kt`):
- Connects to IMAP servers using JavaMail
- Enforces TLS/SSL (unencrypted connections throw `SecurityException`)
- Server certificate validation enabled
- Fetches emails, synchronizes folders

**SMTP Service** (`data/remote/smtp/SMTPService.kt`):
- Sends emails via SMTP using JavaMail
- Enforces TLS/SSL encryption
- Rate limiting: max 20 emails per 5 minutes (implemented in `ComposeViewModel`)

**Credential Storage** (`data/local/CredentialManager.kt`):
- Uses `EncryptedSharedPreferences` for passwords
- AES-256 encryption with Android Keystore
- Credentials excluded from backups (see `res/xml/backup_rules.xml`)

### Security Hardening

This codebase has undergone comprehensive security hardening (December 2025). Critical security implementations:

**Email Header Injection Prevention** (`presentation/compose/ComposeViewModel.kt:195`):
- Validates email addresses against RFC 5322
- Blocks newlines, carriage returns, tabs, null bytes in email fields
- Subject max 998 chars, body max 10MB

**HTML Email XSS Prevention** (`presentation/components/HtmlEmailContent.kt`):
- HTML sanitization using JSoup before WebView rendering
- Content Security Policy: `default-src 'none'; img-src data:; style-src 'unsafe-inline';`
- WebView configured with JavaScript disabled, network loading blocked
- Only data URIs allowed for images (prevents tracking pixels)
- Wrapped in AndroidView composable for Compose integration

**Network Security**:
- TLS certificate validation enforced (no wildcard trust)
- Only TLSv1.2 and TLSv1.3 allowed
- Unencrypted connections blocked

**Data Protection**:
- No debug logging of credentials or sensitive data
- Backups exclude database and credential storage
- Rate limiting on email sending

### UI Architecture (Jetpack Compose)

**Material3 Theme** (`presentation/theme/`):
- Complete Material3 color scheme (light & dark modes)
- Typography system using Material3 text styles
- Theme composable wraps entire app in `MainActivity`

**Compose Components** (`presentation/components/`):
- `EmailListItem` - Reusable email list item with selection mode support
- `HtmlEmailContent` - AndroidView wrapper for WebView HTML rendering
- Uses `combinedClickable` for long-press selection mode

**Selection Mode** (`InboxScreen`, `FolderViewScreen`):
- Multi-select emails using long-press
- `BackHandler` for selection mode exit
- Bulk operations: Archive, Delete, Mark as Read
- Selection state managed in ViewModels

**Multi-Account Support**:
- Account switching managed by ViewModels
- Current account ID stored in `AppPreferences`
- Account list displayed in `SettingsScreen`

**Folder System**:
- System folders: Inbox, Sent, Drafts (represented by `FolderType` enum)
- Custom user folders stored in Room
- Folder navigation via Compose Navigation
- Folder management in `FolderManagementScreen`

### Important Implementation Details

**Jetpack Compose**: UI built with Compose using Material3 components. Compose BOM version 2024.12.01.

**Coroutines**: All async operations use coroutines with proper scope management:
- `viewModelScope` in ViewModels
- `lifecycleScope` in ComponentActivity (MainActivity)
- Repository methods are suspend functions
- Compose screens use `collectAsStateWithLifecycle()` for state observation

**Compose Navigation**: Type-safe navigation using sealed class routes:
- Routes defined in `presentation/navigation/Screen.kt`
- Navigation graph in `presentation/navigation/NavGraph.kt`
- Use `navController.navigate()` with route strings
- Arguments passed via route parameters

**Hilt Integration**:
- ViewModels use `@HiltViewModel` annotation
- Compose screens use `hiltViewModel()` function for injection
- MainActivity extends `ComponentActivity` (not `AppCompatActivity`)

**WorkManager**: Background email sync configured in `workers/EmailSyncWorker.kt`. Uses Hilt for dependency injection via `HiltWorker`.

**KAPT Configuration**: Special KAPT config in `app/build.gradle` for JDK 17 compatibility with `--add-exports` flags. Don't remove these.

**Proguard**: Release builds use minification. Keep rules in `proguard-rules.pro` for JavaMail and other libraries.

## Code Style & Conventions

- Follow existing Kotlin conventions in the codebase
- Use `sealed class` or `enum class` for state representation (see `util/Result.kt`)
- Repository functions return `Result<T>` wrapper for error handling
- ViewModels should not reference Android framework classes (Context, etc.) except `@ApplicationContext`
- Use dependency injection - never instantiate repositories or services directly
- Compose screens should be stateless - state managed in ViewModels
- Use `@Composable` functions following naming conventions (PascalCase for screens)
- Prefer Material3 components over custom implementations
