# Privacy Policy for Vaulta - Smart Expense Tracker

**Last updated:** November 22nd , 2026

**Vaulta - Smart Expense Tracker** ("Vaulta", "we", "us", or "our"), developed by winblader.org, respects your privacy and is committed to protecting the personal data of our users. This Privacy Policy explains how your information is collected, used, and stored when you use the Vaulta Android application (`com.waleed.vaulta.android`).

---

## 1. Information We Collect

### A. Personal Information (When Creating an Account)
When you choose to register or sign in using Email or Google Sign-In, we collect:
- **Name / Display Name:** Used to personalize your dashboard greeting.
- **Email Address:** Used as your account identifier and for account recovery.
- **Phone Number (Optional):** If provided during registration or profile update.

> **Guest Mode:** If you choose **"Continue as Guest"**, no personal information (name, email, phone) is collected or transmitted to any server.

### B. Financial & Expense Data
- **Transaction Records:** Titles, amounts, categories, dates, and types (Expense or Income).
- **Budget Settings:** Monthly budget caps and spending limits.
- **Storage:** By default, all financial data is stored locally on your device in a secure SQLite database (Room). If logged in with an account, your data may be synced with our secure backend (Supabase) so you can access it across devices.

### C. Device Permissions & Services
- **Notifications (`POST_NOTIFICATIONS`):** Used to alert you when your spending approaches or exceeds budget limits, when your account balance falls below zero, and to confirm automatically logged transactions when Auto-Detect is enabled. We do not send marketing notifications.
- **Notification Access Service (`NotificationListenerService` / `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE`):** Optional, strictly opt-in capability used solely for the on-device **Auto-Detect Transactions** feature. Processed 100% locally on-device and never transmitted to external servers.
- **Vibration (`VIBRATE`):** Used to provide haptic feedback during alerts and reminders.
- **Network Access (`INTERNET`, `ACCESS_NETWORK_STATE`):** Used to communicate with Supabase authentication and cloud sync services.

### D. Optional On-Device Transaction Auto-Detection
When you explicitly enable **Auto-Detect Transactions** in your Profile settings and grant Android Notification Access:
- **100% Local On-Device Processing:** Vaulta parses payment alerts from supported banking SMS, cards, and payment apps (e.g., Google Pay, PhonePe, Paytm, Revolut, Wise, PayPal, Monzo, etc.) entirely locally within your device's memory.
- **Zero Raw Text Uploads:** Raw notification contents, sender details, and message bodies are **never saved, cached, or transmitted** to any cloud server or external third party.
- **Strict Data Filtering:** Non-transaction notifications—including One-Time Passwords (OTPs), authentication codes, personal messaging chats, promotional alerts, and ending account balances—are strictly discarded and never logged.
- **User Control & Revocation:** Auto-Detect is disabled by default. You can turn it off at any time directly in the Vaulta Profile screen or revoke Notification Access in your device's Android System Settings.

---

## 2. Third-Party Services
Vaulta integrates with trusted third-party services for authentication and database management:
- **Supabase:** Provides secure user authentication, token management, and optional cloud database sync. (Privacy Policy: https://supabase.com/privacy)
- **Google Play Services / Google Identity:** Facilitates seamless "Continue with Google" sign-in via Android's Credential Manager. (Privacy Policy: https://policies.google.com/privacy)

We do **not** use third-party advertising SDKs, and we do **not** sell, rent, or trade your personal or financial data to data brokers or third parties.

---

## 3. Data Storage & Security
- **Data in Transit:** All network requests (authentication, API sync) are encrypted using HTTPS / TLS encryption.
- **Data at Rest:** Data stored locally on your device is protected by the Android sandbox operating system security. Cloud data is stored behind authenticated, row-level security policies.

---

## 4. Data Retention & Deletion
You maintain complete ownership of your data:
- **Transaction Editing & Modification:** You can edit and update any logged transaction (including title, amount, type, and date) at any time directly in the app. Edits persist immediately to your local database and sync securely to your account.
- **Individual Transaction Deletion:** You can delete any individual expense or income entry at any time via Swipe-to-Delete on the Home or Transaction List screens, or via the Delete button inside the Transaction Receipt dialog. A brief "Undo" action allows instant recovery if dismissed by mistake.
- **In-App Data Reset ("Reset Everything"):** You can instantly erase all stored transactions, reset monthly budget thresholds to zero, and purge auto-detection configurations directly in the application under `Profile > Data & Privacy > Reset Everything`.
- **Operating System Deletion:** You can erase all locally stored data at any time by clearing the application's storage in your Android device's Settings (`Settings > Apps > Vaulta > Storage > Clear Data`) or by uninstalling the application.
- **Account & Cloud Data Deletion:** To request the permanent deletion of your cloud account and associated profile data from our servers, send an email to **winblader.org@gmail.com** with the subject *"Account Deletion Request"*. Your account and data will be permanently removed within 30 days.

---

## 5. Children's Privacy
Vaulta is not directed to individuals under the age of 13. We do not knowingly collect personal information from children under 13.

---

## 6. Changes to This Privacy Policy
We may update this Privacy Policy from time to time. Any changes will be posted within the application and reflected with an updated "Last updated" date.

---

## 7. Contact Us
If you have any questions, feedback, or data privacy requests, please contact us at:

- **Email:** [winblader.org@gmail.com](mailto:winblader.org@gmail.com)
- **Developer / Organization:** winblader.org
