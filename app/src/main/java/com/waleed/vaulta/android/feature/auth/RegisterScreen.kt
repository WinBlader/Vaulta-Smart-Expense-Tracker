package com.waleed.vaulta.android.feature.auth

import android.accounts.AccountManager
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.base.AuthNavigationEvent
import com.waleed.vaulta.android.ui.theme.InterFontFamily
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Typography
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun RegisterScreen(
    navController: NavController,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val googleAccountPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                val derivedName = selectedEmail.substringBefore("@").replace(".", " ").replaceFirstChar { it.uppercase() }
                viewModel.onEvent(RegisterUiEvent.OnGoogleAccountSelected(derivedName, selectedEmail, ""))
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                AuthNavigationEvent.NavigateToHome -> {
                    try {
                        navController.navigate("/home") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    } catch (_: Exception) {
                        navController.navigate("/home")
                    }
                }
                AuthNavigationEvent.NavigateToLogin -> {
                    navController.popBackStack()
                }
                else -> {}
            }
        }
    }

    if (uiState.showGoogleChooser) {
        GoogleAccountChooserDialog(
            existingAccounts = uiState.registeredAccounts,
            onDismiss = { viewModel.onEvent(RegisterUiEvent.OnDismissGoogleChooser) },
            onAccountSelected = { name, email, phone ->
                viewModel.onEvent(RegisterUiEvent.OnGoogleAccountSelected(name, email, phone))
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
                    text = "Create Account",
                    style = Typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                ExpenseTextView(
                    text = "Powered by Supabase Auth",
                    style = Typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }

            // Registration Card
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
                            .background(if (uiState.authTab == AuthTab.EMAIL) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.onEvent(RegisterUiEvent.OnTabSelected(AuthTab.EMAIL)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Email Register",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (uiState.authTab == AuthTab.EMAIL) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (uiState.authTab == AuthTab.PHONE) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { viewModel.onEvent(RegisterUiEvent.OnTabSelected(AuthTab.PHONE)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Mobile Register",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (uiState.authTab == AuthTab.PHONE) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message
                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    uiState.errorMessage?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFFEBEE))
                                .border(1.dp, Color(0xFFFFCDD2), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = error,
                                color = Red,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // Field 1: Username (Required on Register)
                ExpenseTextView(
                    text = "USERNAME",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = { viewModel.onEvent(RegisterUiEvent.OnUsernameChanged(it)) },
                    placeholder = { ExpenseTextView(text = "Choose your username", color = LightGrey) },
                    leadingIcon = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true,
                    textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontFamily = InterFontFamily),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Field 2: Email or Mobile based on Tab
                if (uiState.authTab == AuthTab.EMAIL) {
                    ExpenseTextView(
                        text = "EMAIL ADDRESS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { viewModel.onEvent(RegisterUiEvent.OnEmailChanged(it)) },
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
                        // Editable Country Code
                        OutlinedTextField(
                            value = uiState.countryCode,
                            onValueChange = { input ->
                                val sanitized = if (input.startsWith("+")) {
                                    "+" + input.drop(1).filter { it.isDigit() }.take(4)
                                } else {
                                    "+" + input.filter { it.isDigit() }.take(4)
                                }
                                viewModel.onEvent(RegisterUiEvent.OnCountryCodeChanged(sanitized))
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
                            value = uiState.mobileNumber,
                            onValueChange = { newValue ->
                                val digitsOnly = newValue.filter { it.isDigit() }.take(14)
                                viewModel.onEvent(RegisterUiEvent.OnMobileChanged(digitsOnly))
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

                // Field 3: Password
                ExpenseTextView(
                    text = "PASSWORD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.onEvent(RegisterUiEvent.OnPasswordChanged(it)) },
                    placeholder = { ExpenseTextView(text = "At least 6 characters", color = LightGrey) },
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

                Spacer(modifier = Modifier.height(14.dp))

                // Field 4: Confirm Password
                ExpenseTextView(
                    text = "CONFIRM PASSWORD",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { viewModel.onEvent(RegisterUiEvent.OnConfirmPasswordChanged(it)) },
                    placeholder = { ExpenseTextView(text = "Re-type your password", color = LightGrey) },
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

                // Register Button
                Button(
                    onClick = { viewModel.onEvent(RegisterUiEvent.OnRegisterClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            text = "Register with Supabase",
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

                Spacer(modifier = Modifier.height(14.dp))

                // Google Sign In Button
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
                            viewModel.onEvent(RegisterUiEvent.OnGoogleSignInClicked)
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
                    onClick = { viewModel.onEvent(RegisterUiEvent.OnGuestClicked) },
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

            // Bottom Prompt: Log in link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
                    .constrainAs(bottomPrompt) {
                        top.linkTo(card.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    },
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpenseTextView(text = "Already have an account? ", color = LightGrey, fontSize = 14.sp)
                Text(
                    text = "Log In",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.clickable {
                        viewModel.onEvent(RegisterUiEvent.OnNavigateToLogin)
                    }
                )
            }
        }
    }
}
