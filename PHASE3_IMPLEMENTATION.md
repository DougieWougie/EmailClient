High Priority

  1. Complete OAuth2 Authentication - Infrastructure exists but not implemented (for Gmail, Outlook)
  2. Implement Server-Side Operations - TODOs exist for marking read/flagged on server (EmailRepositoryImpl.kt:162, 172, 182, 192)
  3. Email Composition/Sending - Complete the compose email functionality
  4. Attachment Handling - Download and display email attachments

  Medium Priority

  5. Search Functionality - Search emails by subject, sender, content
  6. Folder Management - Create, rename, delete custom folders
  7. Email Filtering/Rules - Auto-organize emails
  8. Notifications - Push notifications for new emails

  Low Priority

  9. Multiple Account Switching - Quick switch between accounts
  10. Email Signatures - Custom signatures per account
  11. Dark Mode Toggle - Manual theme switching in settings
  12. Export/Backup - Backup email data

  🎯 My Recommendation

  I'd suggest prioritizing Server-Side Operations (marking emails as read/flagged on the server) since:
  - There are already TODOs in the code for this
  - It's essential for a functional email client
  - Your current implementation only updates local database
  - It would complete the core IMAP functionality

