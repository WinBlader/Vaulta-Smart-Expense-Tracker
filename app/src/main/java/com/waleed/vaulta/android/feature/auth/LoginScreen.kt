package com.waleed.vaulta.android.feature.auth

import android.accounts.AccountManager
import android.app.Activity
import com.waleed.vaulta.android.utils.VaultaToast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.auth.PhoneNormalizationUtils
import com.waleed.vaulta.android.ui.theme.InterFontFamily
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Typography
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun LoginScreen(
    navController: NavController,
    authViewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val isLoading by authViewModel.isLoading.collectAsState()
    val errorMessage by authViewModel.errorMessage.collectAsState()
    val successMessage by authViewModel.successMessage.collectAsState()

    var authTab by remember { mutableStateOf(AuthTab.EMAIL) }
    var email by remember { mutableStateOf("") }
    var countryCode by remember { mutableStateOf("+91") }
    var mobileNumber by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localValidationError by remember { mutableStateOf<String?>(null) }
    var showPasswordReset by remember { mutableStateOf(false) }
    var showGoogleChooserDialog by remember { mutableStateOf(false) }

    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                authViewModel.signInWithGoogleAccount(selectedEmail)
            }
        }
    }

    LaunchedEffect(Unit) {
        authViewModel.uiEffect.collect { effect ->
            when (effect) {
                is AuthUiEffect.NavigateToHome -> {
                    navController.navigate("/home") {
                        popUpTo("/login") { inclusive = true }
                    }
                }
                is AuthUiEffect.NavigateToRegister -> {
                    navController.navigate("/signup")
                }
                is AuthUiEffect.ShowToast -> {
                    VaultaToast.show(context, effect.message)
                }
                else -> {}
            }
        }
    }

    LaunchedEffect(successMessage) {
        successMessage?.let {
            VaultaToast.show(context, it)
        }
    }

    if (showPasswordReset) {
        PasswordResetDialog(
            onDismiss = { showPasswordReset = false },
            onSendResetLink = { email ->
                authViewModel.resetPasswordForEmail(email)
                showPasswordReset = false
            }
        )
    }

    if (showGoogleChooserDialog) {
        val existingAccounts = authViewModel.getAllRegisteredAccounts()
        GoogleAccountChooserDialog(
            existingAccounts = existingAccounts,
            onDismiss = { showGoogleChooserDialog = false },
            onAccountSelected = { name, email, phone ->
                authViewModel.signInWithGoogleAccount(email = email, displayName = name, phone = phone)
                showGoogleChooserDialog = false
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (topBar, headerText, card, bottomPrompt) = createRefs()

            Image(
                painter = painterResource(id = R.drawable.ic_topbar),
                contentDescription = null,
                modifier = Modifier.constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 56.dp, start = 24.dp, end = 24.dp)
                    .constrainAs(headerText) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
            ) {
                ExpenseTextView(
                    text = "Welcome Back",
                    style = Typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExpenseTextView(
                    text = "Sign in to your Vaulta account",
                    style = Typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }

            // Login Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(16.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
                    .constrainAs(card) {
                        top.linkTo(headerText.bottom, margin = 20.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
            ) {
                // Tab Selector: Email vs Mobile
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authTab == AuthTab.EMAIL) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                authTab = AuthTab.EMAIL
                                localValidationError = null
                                authViewModel.clearMessages()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Email Login",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (authTab == AuthTab.EMAIL) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (authTab == AuthTab.PHONE) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                authTab = AuthTab.PHONE
                                localValidationError = null
                                authViewModel.clearMessages()
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mobile Login",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (authTab == AuthTab.PHONE) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Banner with clear messaging
                val displayError = localValidationError ?: errorMessage
                AnimatedVisibility(visible = displayError != null) {
                    displayError?.let { error ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Red.copy(alpha = 0.1f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = Red,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (error.contains("register", ignoreCase = true) || error.contains("No account found", ignoreCase = true)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "👉 Tap here to Register",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable {
                                        navController.navigate("/signup")
                                    }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (authTab == AuthTab.EMAIL) {
                    // Field: Email Address
                    ExpenseTextView(
                        text = "EMAIL ADDRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = email,
                        onValueChange = {
                            email = it
                            localValidationError = null
                            authViewModel.clearMessages()
                        },
                        placeholder = { ExpenseTextView(text = "name@example.com", color = LightGrey) },
                        leadingIcon = {
                            Image(
                                painter = painterResource(id = R.drawable.ic_mail),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = InterFontFamily),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                } else {
                    // Field: Mobile with Side-by-side Country Code & Mobile Number
                    ExpenseTextView(
                        text = "MOBILE NUMBER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Country Code
                        OutlinedTextField(
                            value = countryCode,
                            onValueChange = { input ->
                                countryCode = PhoneNormalizationUtils.sanitizeCountryCodeInput(input)
                                localValidationError = null
                                authViewModel.clearMessages()
                            },
                            placeholder = { Text("+91", color = LightGrey, fontSize = 14.sp) },
                            modifier = Modifier.width(84.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )

                        // Mobile Number
                        OutlinedTextField(
                            value = mobileNumber,
                            onValueChange = { input ->
                                mobileNumber = input.filter { it.isDigit() }.take(14)
                                localValidationError = null
                                authViewModel.clearMessages()
                            },
                            placeholder = { ExpenseTextView(text = "9876543210", color = LightGrey) },
                            leadingIcon = {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_phone),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = InterFontFamily,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Field 2: Password
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpenseTextView(
                        text = "PASSWORD",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Forgot?",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { showPasswordReset = true }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        localValidationError = null
                        authViewModel.clearMessages()
                    },
                    placeholder = { ExpenseTextView(text = "Enter your password", color = LightGrey) },
                    leadingIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_lock),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = InterFontFamily),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Login Button
                Button(
                    onClick = {
                        if (authTab == AuthTab.EMAIL) {
                            val trimmed = email.trim()
                            if (trimmed.isEmpty()) {
                                localValidationError = "Please enter your email address."
                                return@Button
                            }
                            if (password.isEmpty()) {
                                localValidationError = "Please enter your password."
                                return@Button
                            }
                            authViewModel.signInWithEmail(trimmed, password)
                        } else {
                            val phoneError = PhoneNormalizationUtils.validatePhone(countryCode, mobileNumber)
                            if (phoneError != null) {
                                localValidationError = phoneError
                                return@Button
                            }
                            if (password.isEmpty()) {
                                localValidationError = "Please enter your password."
                                return@Button
                            }
                            authViewModel.signInWithPhone(countryCode, mobileNumber, password)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Log In with Supabase",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Divider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Text(
                        text = "  or continue with  ",
                        fontSize = 12.sp,
                        color = LightGrey
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Google Sign In via Native Device Google Accounts
                OutlinedButton(
                    onClick = {
                        try {
                            val intent = AccountManager.newChooseAccountIntent(
                                null,
                                null,
                                arrayOf("com.google"),
                                null,
                                null,
                                null,
                                null
                            )
                            googleAccountPickerLauncher.launch(intent)
                        } catch (e: Exception) {
                            showGoogleChooserDialog = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Google Logo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Continue with Google",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Continue as Guest button
                TextButton(
                    onClick = { authViewModel.loginAsGuest() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Skip for now • Continue as Guest",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Bottom Prompt: Register link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .constrainAs(bottomPrompt) {
                        top.linkTo(card.bottom, margin = 20.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpenseTextView(text = "Don't have an account? ", color = LightGrey, fontSize = 14.sp)
                Text(
                    text = "Sign Up",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        navController.navigate("/signup")
                    }
                )
            }
        }
    }
}
