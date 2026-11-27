package com.waleed.vaulta.android.feature.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.ui.theme.InterFontFamily
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.NavRouts

@Composable
fun PrivacyPolicyConsentScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // Intercept system back press: Gate cannot be dismissed or bypassed without acceptance
    BackHandler(enabled = true) {
        // Do nothing or user must tap Decline & Exit to sign out
    }

    var isChecked by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        bottomBar = {
            // Sticky Bottom Action Bar with interactive agreement checkbox and Accept button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Interactive Checkbox Agreement
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { isChecked = !isChecked }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { isChecked = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Zinc,
                                uncheckedColor = MaterialTheme.colorScheme.outline
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I have read and accept the Vaulta Privacy Policy",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Prominent Full-Width "Accept & Continue" Button
                    Button(
                        onClick = {
                            if (isChecked) {
                                val currentKey = authViewModel.currentUser.value?.id?.takeIf { it.isNotBlank() }
                                authViewModel.setPrivacyPolicyAccepted(true, currentKey)
                                navController.navigate(NavRouts.home) {
                                    popUpTo(NavRouts.privacy_policy_consent) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        },
                        enabled = isChecked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Zinc,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = "Accept & Continue",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Secondary Decline & Exit / Sign Out Button
                    OutlinedButton(
                        onClick = {
                            authViewModel.signOut()
                            try {
                                navController.navigate(NavRouts.login) {
                                    popUpTo(navController.graph.id) { inclusive = true }
                                    launchSingleTop = true
                                }
                            } catch (_: Exception) {
                                navController.navigate(NavRouts.login)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Decline & Sign Out",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = InterFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Zinc, Color(0xFF0F3633))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Privacy Shield",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Privacy Policy",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Last updated: November 27th, 2026",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Medium,
                        color = Zinc
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Please review and accept our Privacy Policy to continue using Vaulta.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = InterFontFamily,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Intro text
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Text(
                    text = "Vaulta - Smart Expense Tracker (\"Vaulta\", \"we\", \"us\", or \"our\"), developed by winblader.org, respects your privacy and is committed to protecting the personal data of our users. This Privacy Policy explains how your information is collected, used, and stored when you use the Vaulta Android application (com.waleed.vaulta.android).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = InterFontFamily,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 1: Information We Collect
            PolicySectionHeader(number = "1", title = "Information We Collect")

            PolicySubheading(title = "A. Personal Information (When Creating an Account)")
            PolicyBodyText(text = "When you choose to register or sign in using Email or Google Sign-In, we collect:\n• Name / Display Name: Used to personalize your dashboard greeting.\n• Email Address: Used as your account identifier and for account recovery.\n• Phone Number (Optional): If provided during registration or profile update.")

            // Highlighted Callout for Guest Mode
            HighlightCard(
                title = "Guest Mode",
                description = "If you choose \"Continue as Guest\", no personal information (name, email, phone) is collected or transmitted to any server.",
                accentColor = Zinc
            )

            Spacer(modifier = Modifier.height(12.dp))
            PolicySubheading(title = "B. Financial & Expense Data")
            PolicyBodyText(text = "• Transaction Records: Titles, amounts, categories, dates, types (Expense, Income, Credit, Owe), and optional transaction notes.\n• Budget Settings: Monthly budget caps and spending limits.\n• Account, Budget & Balance Backup: Profile preferences—including currency, monthly budget limits, and user-set initial and current balance figures—are stored locally on your device in a secure SQLite database (Room) and, when signed in, synced to our secure backend for backup and cross-device restore. In Guest Mode, all balance and transaction data remains strictly on your device.\n• Shared Splits & Universal Ledger: Group expense splits and bilateral credit/owe entries created by you or shared with your contact identifier (email or phone). When you create a group bill split or Credit/Owe entry and optionally enter another participant's email address or phone number, that split record (title, split amount, settlement status, and participant identifier) is synced to our secure backend so both participants can view and settle the shared balance. Entries created without a linked email or phone number remain private to your own account and device. Participants are entered manually without accessing your address book or device contacts.\n• Storage: By default, all financial data is stored locally on your device in a secure SQLite database (Room). If logged in with an account, your data may be synced with our secure backend so you can access it across devices.")

            Spacer(modifier = Modifier.height(12.dp))
            PolicySubheading(title = "C. Device Permissions & Services")
            PolicyBodyText(text = "• Notifications (POST_NOTIFICATIONS): Used to alert you when your spending approaches or exceeds budget limits, when your account balance falls below zero, and to confirm automatically logged transactions when Auto-Detect is enabled. We do not send marketing notifications.\n• Notification Access Service (BIND_NOTIFICATION_LISTENER_SERVICE): Optional, strictly opt-in capability used solely for the on-device Auto-Detect Transactions feature to read incoming SMS / text messaging alerts. User info is processed locally on your device and never transmitted to external servers. Chat apps (WhatsApp, Telegram, Instagram, Discord, etc.) are completely excluded.\n• Vibration (VIBRATE): Used to provide haptic feedback during alerts and reminders.\n• Network Access (INTERNET, ACCESS_NETWORK_STATE): Used to communicate with backend authentication and cloud sync services.")

            Spacer(modifier = Modifier.height(12.dp))
            PolicySubheading(title = "D. Optional On-Device Transaction Auto-Detection (SMS Only)")

            // Highlighted Callout for SMS-Only Auto-Detection
            HighlightCard(
                title = "SMS-Only On-Device Auto-Detection",
                description = "• Strictly Limited to SMS Messaging Apps: Auto-detection exclusively listens to notifications from standard SMS/text messaging apps (Google Messages, Samsung Messages, or your default SMS app). Vaulta strictly blocks and ignores chat, messaging, and social apps (including WhatsApp, Telegram, Instagram, Discord, Facebook Messenger, Slack, etc.), preventing casual conversations from ever being detected.\n• 100% Local On-Device Processing: User info is processed locally on your device.\n• Zero Raw Text Uploads: Raw notification contents, sender details, and message bodies are never saved, cached, or transmitted to any cloud server or external third party.\n• Strict Data Filtering: Non-transaction notifications—including One-Time Passwords (OTPs), authentication codes, personal messaging texts, promotional alerts, and ending account balances—are strictly discarded and never logged.\n• User Control & Revocation: Auto-Detect is disabled by default. You can turn it off at any time directly in the Vaulta Profile screen or revoke Notification Access in your device's Android System Settings.",
                accentColor = Color(0xFF00796B)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 2: Third-Party Services
            PolicySectionHeader(number = "2", title = "Third-Party Services")
            PolicyBodyText(text = "Vaulta integrates with trusted third-party services for authentication and database management:\n• Secure Backend: Provides secure user authentication, token management, and optional cloud database sync.\n• Google Play Services / Google Identity: Facilitates seamless \"Continue with Google\" sign-in via Android's Credential Manager. (Privacy Policy: https://policies.google.com/privacy)\n\nWe do not use third-party advertising SDKs, and we do not sell, rent, or trade your personal or financial data to data brokers or third parties.")

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: Data Storage & Security
            PolicySectionHeader(number = "3", title = "Data Storage & Security")
            PolicyBodyText(text = "• Data in Transit: All network requests (authentication, API sync) are encrypted using HTTPS / TLS encryption.\n• Data at Rest: Data stored locally on your device is protected by the Android sandbox operating system security. Cloud data is stored behind authenticated, row-level security policies.")

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: Data Retention & Deletion
            PolicySectionHeader(number = "4", title = "Data Retention & Deletion")
            PolicyBodyText(text = "You maintain complete ownership of your data:\n• Transaction Editing & Modification: You can edit and update any logged transaction at any time directly in the app. Edits persist immediately to your local database and sync securely to your account.\n• Individual Transaction Deletion: You can delete any individual expense or income entry at any time via Swipe-to-Delete or via the Delete button inside the Transaction Receipt dialog. A brief \"Undo\" action allows instant recovery if dismissed by mistake.\n• In-App Data Reset (\"Reset Everything\"): You can instantly erase all stored transactions, reset monthly budget thresholds to zero, and purge auto-detection configurations directly in the application under Profile > Data & Privacy > Reset Everything.\n• Operating System Deletion: You can erase all locally stored data at any time by clearing the application's storage in your Android device's Settings (Settings > Apps > Vaulta > Storage > Clear Data) or by uninstalling the application.\n• Account & Cloud Data Deletion: To request the permanent deletion of your cloud account and associated profile data from our servers, send an email to winblader.org@gmail.com with the subject \"Account Deletion Request\". Your account and data will be permanently removed within 30 days.")

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 5: Children's Privacy
            PolicySectionHeader(number = "5", title = "Children's Privacy")
            PolicyBodyText(text = "Vaulta is not directed to individuals under the age of 13. We do not knowingly collect personal information from children under 13.")

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 6: Changes to This Privacy Policy
            PolicySectionHeader(number = "6", title = "Changes to This Privacy Policy")
            PolicyBodyText(text = "We may update this Privacy Policy from time to time. Any changes will be posted within the application and reflected with an updated \"Last updated\" date.")

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 7: Contact Us
            PolicySectionHeader(number = "7", title = "Contact Us")
            PolicyBodyText(text = "If you have any questions, feedback, or data privacy requests, please contact us at:\n• Email: winblader.org@gmail.com\n• Developer / Organization: winblader.org")

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun PolicySectionHeader(number: String, title: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Zinc),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = InterFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = InterFontFamily,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
private fun PolicySubheading(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun PolicyBodyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = InterFontFamily,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun HighlightCard(
    title: String,
    description: String,
    accentColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp)
            ),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = accentColor.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = InterFontFamily,
                    lineHeight = 19.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    }
}
