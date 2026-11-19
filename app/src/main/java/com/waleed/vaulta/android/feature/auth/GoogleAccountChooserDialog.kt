package com.waleed.vaulta.android.feature.auth

import android.accounts.AccountManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.model.UserProfile
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun GoogleAccountChooserDialog(
    existingAccounts: List<UserProfile> = emptyList(),
    onDismiss: () -> Unit,
    onAccountSelected: (name: String, email: String, phone: String) -> Unit
) {
    val context = LocalContext.current
    var customName by remember { mutableStateOf("") }
    var customEmail by remember { mutableStateOf("") }
    var customPhone by remember { mutableStateOf("") }
    var showCustomInput by remember { mutableStateOf(existingAccounts.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Read device Google accounts safely
    val deviceGoogleAccounts = remember {
        try {
            val accountManager = AccountManager.get(context)
            accountManager.getAccountsByType("com.google").map { it.name }
        } catch (e: Exception) {
            emptyList<String>()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = "Google",
                    modifier = Modifier.size(26.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                ExpenseTextView(
                    text = "Sign in with Google",
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    color = Color.Black
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ExpenseTextView(
                    text = "Choose an account to continue to Vaulta",
                    fontSize = 13.sp,
                    color = LightGrey
                )
                Spacer(modifier = Modifier.height(14.dp))

                // List existing registered Google accounts
                val googleRegistered = existingAccounts.filter { it.authProvider.contains("Google", ignoreCase = true) }
                if (googleRegistered.isNotEmpty()) {
                    ExpenseTextView(
                        text = "SAVED ACCOUNTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (acc in googleRegistered) {
                        GoogleAccountRow(
                            name = acc.name,
                            email = acc.email,
                            onClick = {
                                onAccountSelected(acc.name, acc.email, acc.phoneNumber)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                }

                // If device Google accounts detected
                if (deviceGoogleAccounts.isNotEmpty()) {
                    ExpenseTextView(
                        text = "DEVICE GOOGLE ACCOUNTS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (devEmail in deviceGoogleAccounts) {
                        val derivedName = devEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                        GoogleAccountRow(
                            name = derivedName,
                            email = devEmail,
                            onClick = {
                                onAccountSelected(derivedName, devEmail, "+91 9876543210")
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFFEEEEEE))
                }

                if (!showCustomInput) {
                    TextButton(
                        onClick = { showCustomInput = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExpenseTextView(
                            text = "+ Add / Sign in with another Google account",
                            color = Zinc,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                } else {
                    ExpenseTextView(
                        text = "ENTER GOOGLE ACCOUNT DETAILS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    ExpenseTextView(text = "FULL NAME", fontSize = 11.sp, color = LightGrey)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it; errorMessage = null },
                        placeholder = { ExpenseTextView("e.g. Waleed Wani", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExpenseTextView(text = "GOOGLE EMAIL (GMAIL)", fontSize = 11.sp, color = LightGrey)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it; errorMessage = null },
                        placeholder = { ExpenseTextView("e.g. waleed@gmail.com", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    ExpenseTextView(text = "LINKED MOBILE NUMBER", fontSize = 11.sp, color = LightGrey)
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customPhone,
                        onValueChange = {
                            customPhone = it.filter { ch -> ch.isDigit() }.take(10)
                            errorMessage = null
                        },
                        placeholder = { ExpenseTextView("10-digit mobile number", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                    )

                    if (errorMessage != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ExpenseTextView(
                            text = errorMessage ?: "",
                            color = Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = {
                            val name = customName.trim()
                            val email = customEmail.trim()
                            val phone = customPhone.trim()

                            if (name.isEmpty()) {
                                errorMessage = "Please enter your name"
                                return@Button
                            }
                            if (email.isEmpty() || !email.contains("@")) {
                                errorMessage = "Please enter a valid Google email (@gmail.com)"
                                return@Button
                            }
                            if (phone.isNotEmpty() && phone.length < 10) {
                                errorMessage = "Please enter a valid 10-digit mobile number"
                                return@Button
                            }

                            val formattedPhone = if (phone.isNotEmpty()) "+91 $phone" else "+91 9876543210"
                            onAccountSelected(name, email, formattedPhone)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        ExpenseTextView("Continue with this Google Account", color = Color.White)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                ExpenseTextView(text = "Cancel", color = LightGrey)
            }
        }
    )
}

@Composable
fun GoogleAccountRow(
    name: String,
    email: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF7F9FA))
            .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFE0E0E0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                ExpenseTextView(
                    text = name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color.Black
                )
                ExpenseTextView(
                    text = email,
                    color = LightGrey,
                    fontSize = 12.sp
                )
            }
        }
    }
}
