# Android Email Client

A modern, secure Android email client supporting IMAP/SMTP protocols with multi-account management and comprehensive security hardening.

## Project Status

**Phase 1 Foundation - Complete** ✅
**Phase 2 UI Implementation - In Progress** 🚧

**Build Status**: ✅ Successfully building (both debug and release)
**Last Build**: December 13, 2025
**APK Outputs**:
- Debug: ~8.7 MB
- Release: ~2.8 MB (minified with R8)

The project has evolved beyond initial foundation with working UI components:
- MVVM architecture with Repository pattern
- Room database for local email caching
- Hilt for dependency injection
- Material Design 3 UI components
- Navigation Component with drawer navigation
- Interactive email list with swipe gestures
- Multi-account management with setup wizard

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
│   │   ├── dao/              # Room DAOs (AccountDao, FolderDao)
│   │   ├── entities/         # Room entities (EmailEntity, AccountEntity, FolderEntity)
│   │   ├── AppDatabase.kt
│   │   └── AppPreferences.kt # Shared preferences wrapper
│   ├── remote/               # Future: IMAP/SMTP services
│   └── repository/           # Repository implementations
├── domain/
│   ├── model/               # Domain models (Email, Account, Folder, SwipeAction)
│   └── repository/          # Repository interfaces
├── presentation/
│   ├── setup/               # Account setup wizard (WelcomeFragment, ManualConfigFragment)
│   ├── inbox/               # Email inbox with swipe actions
│   ├── folder/              # Folder view for custom folders
│   ├── folders/             # Folder management screen
│   ├── detail/              # Email detail viewer
│   ├── compose/             # Email composition
│   ├── settings/            # App settings and account list
│   ├── common/              # Shared UI components (EmailSwipeCallback)
│   └── MainActivity.kt      # Main app with navigation drawer
├── di/                      # Hilt dependency injection modules
├── util/                    # Utilities (Result wrapper, extensions)
└── EmailApplication.kt
```

## Features Implemented

### Foundation
- ✅ Room database schema (accounts, emails, folders)
- ✅ Repository pattern with interfaces
- ✅ Navigation graph with drawer navigation
- ✅ ViewModels with StateFlow
- ✅ Dark/light theme support
- ✅ RecyclerView with DiffUtil
- ✅ Dependency injection with Hilt

### UI & User Experience
- ✅ **Account Setup Wizard** - Welcome screen and manual configuration flow
- ✅ **Account Management** - Edit existing accounts, multi-account support
- ✅ **Navigation Drawer** - Easy access to folders and settings
- ✅ **Email List View** - Inbox with read/unread states, attachments indicators
- ✅ **Swipe Actions** - Configurable left/right swipe gestures (archive, delete, mark as read)
- ✅ **Multi-Select Mode** - Select multiple emails for batch operations
- ✅ **Folder Management** - Create and manage custom email folders
- ✅ **Email Detail View** - Read individual emails with folder move support
- ✅ **Compose Email** - Basic email composition interface

### Pending Implementation
- ⏳ IMAP email fetching (JavaMail integration)
- ⏳ SMTP email sending (JavaMail integration)
- ⏳ Secure credential storage (EncryptedSharedPreferences)
- ⏳ Background sync with WorkManager
- ⏳ OAuth2 authentication (Gmail, Outlook)
- ⏳ HTML email rendering
- ⏳ Attachment handling (upload/download)
- ⏳ Push notifications
- ⏳ Search functionality
- ⏳ Email signatures

## Using the Application

### First-Time Setup

1. **Launch the App**: On first launch, you'll be greeted with the welcome screen
2. **Add Your First Account**:
   - Tap "Add Account" or "Manual Configuration"
   - Enter your email address
   - Choose between automatic detection or manual configuration
3. **Manual Configuration**:
   - **IMAP Settings** (Incoming Mail):
     - Server: Your IMAP server address (e.g., `imap.gmail.com`)
     - Port: Typically 993 for SSL/TLS, 143 for STARTTLS
     - Security: Choose SSL/TLS or STARTTLS (unencrypted connections are disabled for security)
     - Username: Your full email address
     - Password: Your email password or app-specific password
   - **SMTP Settings** (Outgoing Mail):
     - Server: Your SMTP server address (e.g., `smtp.gmail.com`)
     - Port: Typically 465 for SSL/TLS, 587 for STARTTLS
     - Security: Choose SSL/TLS or STARTTLS
     - Username: Your full email address
     - Password: Same as IMAP password
4. **Save Account**: Your credentials are securely stored using Android's EncryptedSharedPreferences

### Reading Emails

1. **View Your Inbox**: The main screen displays your inbox with:
   - **Unread emails** shown in bold
   - **Attachment indicator** for emails with attachments
   - **Date/time** of each email
   - **Sender** name and email address
2. **Open an Email**: Tap any email to view its full content
3. **HTML Rendering**: HTML emails are automatically rendered with:
   - Sanitized content to prevent XSS attacks
   - Dark/light theme support
   - Network images blocked by default for privacy
4. **Move to Folder**: Use the folder dropdown to move emails between folders
5. **Navigate**: Use the back button to return to the email list

### Managing Emails

#### Swipe Gestures
Customize quick actions by swiping on emails:
- **Swipe Left**: Perform your configured action (Archive, Delete, or Mark as Read)
- **Swipe Right**: Perform your configured action (Archive, Delete, or Mark as Read)
- Configure swipe actions in Settings

#### Multi-Select Mode
1. Long-press any email to enter selection mode
2. Tap additional emails to select them
3. Use the toolbar actions to:
   - Delete selected emails
   - Mark as read/unread
   - Move to a folder
   - Archive emails

### Composing Emails

1. **New Email**: Tap the compose button (floating action button)
2. **Fill in Details**:
   - **To**: Comma-separated email addresses (validated for security)
   - **Cc**: Carbon copy recipients (optional)
   - **Bcc**: Blind carbon copy recipients (optional)
   - **Subject**: Email subject (max 998 characters)
   - **Body**: Email content (max 10 MB)
3. **Send**: Tap the send button
4. **Rate Limiting**: You can send up to 20 emails per 5 minutes (anti-spam protection)

### Reply and Forward

1. **Open an Email**: View the email you want to reply to
2. **Reply**: Tap the reply button to compose a response
3. **Forward**: Tap the forward button to forward the email to others
4. **Original Content**: The original email is automatically quoted

### Managing Folders

1. **Navigate to Folders**: Open the navigation drawer and select "Folders"
2. **View Folders**: See all your folders (Inbox, Sent, Drafts, Custom folders)
3. **Create Folder**: Tap the add button to create a new custom folder
4. **Switch Folders**: Select any folder from the navigation drawer to view its contents

### Multiple Accounts

1. **Add Another Account**: Settings → Accounts → Add Account
2. **Switch Accounts**: Use the account selector in the navigation drawer
3. **Edit Account**: Settings → Accounts → Tap on an account to edit
4. **Delete Account**: Edit account screen → Delete option

### Settings

Access settings from the navigation drawer:
- **Accounts**: Manage email accounts (add, edit, delete)
- **Swipe Actions**: Configure left and right swipe gestures
- **Theme**: Dark/light mode (follows system default)
- **Sync Settings**: Configure background sync (coming soon)

## Security

This application has been hardened against common security vulnerabilities. A comprehensive security audit was conducted in December 2025, resulting in fixes for 11 critical and high-severity vulnerabilities.

### Security Fixes Summary

| Category | Vulnerabilities Fixed | Severity |
|----------|----------------------|----------|
| **Credential Security** | Password logging in system logs | High |
| **Network Security** | Disabled TLS certificate validation | Critical |
| **Network Security** | Unencrypted connections allowed | Critical |
| **Injection Attacks** | Email header injection | High |
| **Web Security** | Cross-Site Scripting (XSS) in HTML emails | Critical |
| **Web Security** | Missing Content Security Policy | High |
| **Privacy** | Network tracking via WebView | Medium |
| **Denial of Service** | ReDoS via complex regex | Medium |
| **Denial of Service** | No email sending rate limiting | Medium |
| **Data Protection** | Sensitive data in backups | High |
| **Information Disclosure** | Debug logging of sensitive data | Medium |

### Detailed Security Implementations

#### Authentication & Credentials

**Encrypted Credential Storage**
- **Implementation**: All passwords are stored using Android's `EncryptedSharedPreferences` (app/src/main/java/com/emailclient/data/local/CredentialManager.kt:38)
- **Protection**: AES-256 encryption with keys stored in Android Keystore
- **Backup Exclusion**: Credentials are explicitly excluded from backups and cloud syncs

**Password Logging Prevention** ✅
- **Vulnerability Fixed**: Debug logging that exposed passwords in plain text
- **Impact**: Prevented password exposure in system logs
- **Changes**: Removed all debug log statements containing passwords or credential information
- **Files**: app/src/main/java/com/emailclient/data/local/CredentialManager.kt:38

#### Network Security

**TLS Certificate Validation** ✅
- **Vulnerability Fixed**: Disabled certificate validation (`ssl.trust = "*"`, `checkserveridentity = "false"`)
- **Impact**: Prevented man-in-the-middle attacks
- **Implementation**:
  - Enabled proper server identity checking: `mail.imap.ssl.checkserveridentity = "true"`
  - Enabled proper server identity checking: `mail.smtp.ssl.checkserveridentity = "true"`
  - Removed wildcard trust configurations
- **Files**:
  - app/src/main/java/com/emailclient/data/remote/imap/IMAPService.kt:43
  - app/src/main/java/com/emailclient/data/remote/smtp/SMTPService.kt:83

**Forced Encryption** ✅
- **Vulnerability Fixed**: Allowed unencrypted connections (`SecurityType.NONE`)
- **Impact**: All email communications now require encryption
- **Implementation**: Throws `SecurityException` if unencrypted connection is attempted
- **Supported Protocols**: TLSv1.2 and TLSv1.3 only
- **Files**:
  - app/src/main/java/com/emailclient/data/remote/imap/IMAPService.kt:51
  - app/src/main/java/com/emailclient/data/remote/smtp/SMTPService.kt:91

#### Email Injection Prevention

**Email Header Injection** ✅
- **Vulnerability Fixed**: No validation of email addresses allowed injection of additional headers
- **Impact**: Prevented email header injection attacks (RFC 5322 violations)
- **Implementation**:
  - Validates email format using regex pattern
  - Blocks emails containing newlines (`\n`), carriage returns (`\r`), tabs (`\t`), or null bytes (`\u0000`)
- **Files**: app/src/main/java/com/emailclient/presentation/compose/ComposeViewModel.kt:195

**Input Validation** ✅
- **Subject Length**: Maximum 998 characters (RFC 5322 compliant)
- **Body Size**: Maximum 10 MB to prevent memory exhaustion
- **Null Byte Detection**: Blocks null bytes in subject and body
- **Files**: app/src/main/java/com/emailclient/presentation/compose/ComposeViewModel.kt:120

#### Web Security (HTML Emails)

**XSS (Cross-Site Scripting) Prevention** ✅
- **Vulnerability Fixed**: Unsanitized HTML email rendering in WebView
- **Impact**: Prevented malicious JavaScript execution in email content
- **Implementation**:
  - HTML sanitization using JSoup with relaxed Safelist
  - Allowed tags: div, span, p, br, table, etc.
  - Allowed protocols: http, https, mailto, data (for images)
  - Removes all `<script>`, `<iframe>`, and other dangerous tags
- **Files**: app/src/main/java/com/emailclient/presentation/detail/EmailDetailFragment.kt:362
- **Dependency**: JSoup 1.17.2 (app/build.gradle:145)

**Content Security Policy** ✅
- **Implementation**: CSP meta tag in HTML email rendering
- **Policy**: `default-src 'none'; img-src data:; style-src 'unsafe-inline';`
- **Effect**: Blocks all external resources except inline styles and data URIs
- **Files**: app/src/main/java/com/emailclient/presentation/detail/EmailDetailFragment.kt:385

**Network Access Control** ✅
- **Vulnerability Fixed**: WebView allowed network image loading and external requests
- **Impact**: Prevents tracking pixels and external resource loading
- **Implementation**:
  - `blockNetworkImage = true`
  - `blockNetworkLoads = true`
  - Only data URIs are allowed for images
- **Files**: app/src/main/java/com/emailclient/presentation/detail/EmailDetailFragment.kt:166

**WebView Security Hardening**
- **JavaScript**: Disabled by default
- **File Access**: Disabled (`allowFileAccess = false`)
- **Content Access**: Disabled (`allowContentAccess = false`)
- **Mixed Content**: Never allowed (`MIXED_CONTENT_NEVER_ALLOW`)
- **Safe Browsing**: Enabled
- **Files**: app/src/main/java/com/emailclient/presentation/detail/EmailDetailFragment.kt:153

#### Denial of Service Prevention

**ReDoS (Regular Expression DoS)** ✅
- **Vulnerability Fixed**: Complex regex pattern for HTML tag detection vulnerable to ReDoS
- **Impact**: Prevented CPU exhaustion from maliciously crafted email content
- **Implementation**: Replaced regex with simple string matching using `contains()`
- **Files**:
  - app/src/main/java/com/emailclient/data/remote/imap/IMAPService.kt:341
  - app/src/main/java/com/emailclient/presentation/detail/EmailDetailFragment.kt:345

**Email Sending Rate Limiting** ✅
- **Implementation**: Maximum 20 emails per 5-minute window
- **Protection**: Prevents spam and abuse
- **Mechanism**: In-memory timestamp tracking with automatic cleanup
- **Files**: app/src/main/java/com/emailclient/presentation/compose/ComposeViewModel.kt:221

#### Data Protection

**Backup Security** ✅
- **Vulnerability Fixed**: Sensitive data included in backups
- **Implementation**: Excluded from Android Auto Backup and Cloud Backup:
  - Encrypted credentials (`secure_email_credentials.xml`)
  - App preferences (`app_preferences.xml`)
  - Email database (`email_client_db`, `email_client_db-shm`, `email_client_db-wal`)
- **Files**:
  - app/src/main/res/xml/backup_rules.xml:3
  - app/src/main/res/xml/data_extraction_rules.xml:4

#### Debug Information Leakage

**Production Logging** ✅
- **Removed**: Debug logging from IMAP/SMTP services
- **Impact**: Prevents sensitive information from appearing in logs
- **Scope**: Removed connection details, credentials, and message content from logs
- **Files**:
  - app/src/main/java/com/emailclient/data/remote/imap/IMAPService.kt:74
  - app/src/main/java/com/emailclient/data/remote/smtp/SMTPService.kt:34

#### Security Best Practices

- **Principle of Least Privilege**: App only requests necessary permissions
- **Secure by Default**: All security features enabled by default
- **Defense in Depth**: Multiple layers of security (encryption, validation, sanitization)
- **Input Validation**: All user inputs validated before processing
- **Output Encoding**: All HTML output sanitized before rendering

#### Security Recommendations for Users

1. **Use App-Specific Passwords**: For Gmail and other providers, use app-specific passwords instead of your main account password
2. **Enable 2FA**: Enable two-factor authentication on your email accounts
3. **Review Permissions**: Only grant necessary permissions to the app
4. **Keep Updated**: Install app updates promptly for latest security fixes
5. **Avoid Public WiFi**: Use VPN when accessing email on public networks
6. **Report Issues**: Report any security concerns to the maintainers

## User Experience Highlights

### Swipe Gestures
Customize swipe actions for quick email management:
- **Swipe Left**: Archive, Delete, or Mark as Read
- **Swipe Right**: Archive, Delete, or Mark as Read
- Configurable per-user preferences
- Visual feedback with colored backgrounds and icons

### Multi-Account Support
- Add multiple email accounts (IMAP/SMTP)
- Switch between accounts seamlessly
- Edit account settings at any time
- Welcome wizard for first-time setup

### Folder Management
- View emails by folder (Inbox, Sent, Drafts, Custom)
- Create custom folders for organization
- Move emails between folders with ease
- Folder list in navigation drawer

## Building the Project

### Prerequisites
- Android Studio Iguana or later
- **JDK 17** (Required - JDK 21 has KAPT compatibility issues)
- Android SDK 34

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all checks (lint + tests)
./gradlew build
```

### Steps
1. Clone the repository
2. Ensure Java 17 is installed and active
3. Open in Android Studio
4. Sync Gradle files
5. Build and run

### Build Output
- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Testing

For detailed build test results, see [BUILD_TEST_REPORT.md](BUILD_TEST_REPORT.md)

### Test Status
- ✅ Build: Successfully compiles
- ✅ Lint: Passing with warnings
- ⏳ Unit Tests: Framework ready, tests to be implemented
- ⏳ Integration Tests: Framework ready, tests to be implemented

## Next Steps - Phase 3: Email Protocol Implementation

With the UI foundation complete, the next phase focuses on connecting to real email servers:

1. **IMAP Email Fetching**
   - Integrate JavaMail API for IMAP connections
   - Implement secure authentication (TLS/SSL)
   - Parse MIME messages and store in Room database
   - Handle folder synchronization
   - Support for IDLE push notifications

2. **SMTP Email Sending**
   - Integrate JavaMail API for SMTP
   - Support attachments and multi-part messages
   - Handle sent folder synchronization
   - Queue failed sends for retry

3. **Credential Security**
   - Migrate to EncryptedSharedPreferences for passwords
   - Implement OAuth2 flows for Gmail and Outlook
   - Secure token storage and refresh

4. **Background Synchronization**
   - WorkManager periodic sync workers
   - Conflict resolution strategies
   - Battery-optimized sync intervals
   - Foreground service for ongoing operations

5. **Content Rendering**
   - HTML email rendering with WebView
   - Inline image support
   - Attachment preview and download
   - Rich text composition

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
