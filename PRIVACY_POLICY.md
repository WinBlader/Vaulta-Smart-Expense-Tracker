# Privacy Policy for Vaulta - Smart Expense Tracker

**Last updated:** November 19, 2026

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

### C. Device Permissions
- **Notifications (`POST_NOTIFICATIONS`):** Used solely to alert you when your spending exceeds set budget limits or when your account balance falls below zero. We do not use notifications for marketing.
- **Vibration (`VIBRATE`):** Used to provide haptic feedback during alerts and reminders.
- **Network Access (`INTERNET`, `ACCESS_NETWORK_STATE`):** Used to communicate with Supabase authentication and Google Sign-In services.

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
- **Local Data Deletion:** You can erase all locally stored data at any time by clearing the application's storage in your Android device's Settings (`Settings > Apps > Vaulta > Storage > Clear Data`) or by uninstalling the application.
- **Account & Cloud Data Deletion:** To request the permanent deletion of your cloud account and associated data from our servers, send an email to **winblader.org@gmail.com** with the subject *"Account Deletion Request"*. Your account and data will be permanently removed within 30 days.

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
