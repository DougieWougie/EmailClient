# Android Email Client

A modern Android email client supporting IMAP/SMTP protocols with multi-account management.

## Project Status

**Phase 1 Foundation - Complete** ✓

The project structure has been fully set up with clean architecture principles:
- MVVM architecture with Repository pattern
- Room database for local email caching
- Hilt for dependency injection
- Material Design 3 UI components
- Navigation Component for screen navigation

## Tech Stack

- **Language**: Kotlin
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM + Repository Pattern
- **DI**: Hilt
- **Database**: Room
- **UI**: XML Views with ViewBinding
- **Email**: JavaMail API (to be implemented)
- **Networking**: Retrofit2 + OkHttp3
- **Async**: Kotlin Coroutines + Flow

## Project Structure

```
app/src/main/java/com/emailclient/
├── data/
│   ├── local/
│   │   ├── dao/           # Room DAOs
│   │   ├── entities/      # Room entities
│   │   └── AppDatabase.kt
│   ├── remote/            # Future: API services
│   └── repository/        # Repository implementations
├── domain/
│   ├── model/            # Domain models (Email, Account, Folder)
│   └── repository/       # Repository interfaces
├── presentation/
│   ├── inbox/            # Inbox screen
│   ├── detail/           # Email detail screen
│   ├── compose/          # Compose email screen
│   ├── settings/         # Settings screen
│   └── MainActivity.kt
├── di/                   # Hilt modules
├── util/                 # Utilities (Result wrapper)
└── EmailApplication.kt
```

## Features Implemented

### Foundation (Current)
- ✓ Room database schema (accounts, emails, folders)
- ✓ Repository pattern with interfaces
- ✓ Navigation graph
- ✓ Base UI fragments and layouts
- ✓ ViewModels with StateFlow
- ✓ Dark/light theme support
- ✓ RecyclerView with DiffUtil

### Pending Implementation
- ⏳ IMAP email fetching (JavaMail)
- ⏳ SMTP email sending (JavaMail)
- ⏳ Account credential storage (EncryptedSharedPreferences)
- ⏳ WorkManager for background sync
- ⏳ OAuth2 flows (Gmail, Outlook)
- ⏳ HTML email rendering
- ⏳ Attachment handling
- ⏳ Push notifications

## Building the Project

### Prerequisites
- Android Studio Iguana or later
- JDK 17
- Android SDK 34

### Steps
1. Clone the repository
2. Open in Android Studio
3. Sync Gradle files
4. Build and run

## Next Steps - Phase 1 Implementation

1. **Implement IMAP Email Fetching**
   - Create IMAPService using JavaMail
   - Implement connection management
   - Parse emails and store in Room database

2. **Implement SMTP Email Sending**
   - Create SMTPService using JavaMail
   - Handle authentication
   - Send plain text emails

3. **Credential Management**
   - Implement EncryptedSharedPreferences
   - Secure password storage
   - Account setup flow

4. **Background Sync**
   - Implement WorkManager workers
   - Periodic email sync
   - Handle sync conflicts

## Architecture Decisions

### Why Repository Pattern?
Abstracts data sources (local database, remote server) from the UI layer, making the code more testable and maintainable.

### Why Room Database?
Provides offline-first capability, allowing users to read emails without network connectivity.

### Why Hilt?
Type-safe dependency injection reduces boilerplate and makes the codebase more modular.

### Why Flow over LiveData?
Flow provides more powerful operators and better integration with Kotlin Coroutines.

## Development Guidelines

- Follow Clean Architecture principles
- Keep business logic in ViewModels
- Use Repository for all data operations
- Mark TODO comments for future implementations
- Write unit tests for business logic
- Use meaningful commit messages

## License

This project is for educational purposes.
