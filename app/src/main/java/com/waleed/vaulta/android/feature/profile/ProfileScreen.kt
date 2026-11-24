package com.waleed.vaulta.android.feature.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.base.ProfileNavigationEvent
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Typography
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.widget.ExpenseTextView
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CurrencyItem


@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val user by viewModel.user.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val currentThemeMode by viewModel.themeMode.collectAsState()
    val currentCurrency by viewModel.selectedCurrency.collectAsState()
    val isAutoDetectEnabled by viewModel.isAutoDetectEnabled.collectAsState()
    val isNotificationAccessGranted by viewModel.isNotificationAccessGranted.collectAsState()
    val isServiceConnected by viewModel.isServiceConnected.collectAsState()
    val isIgnoringBatteryOptimizations by viewModel.isIgnoringBatteryOptimizations.collectAsState()
    var showCurrencyDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.onEvent(ProfileUiEvent.OnResume)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            com.waleed.vaulta.android.utils.VaultaToast.show(context, msg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                ProfileNavigationEvent.NavigateToLogin -> {
                    try {
                        navController.navigate("/login") {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    } catch (_: Exception) {
                        navController.navigate("/login")
                    }
                }
                else -> {}
            }
        }
    }

    if (uiState.showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ProfileUiEvent.OnDismissLogout) },
            title = {
                ExpenseTextView(
                    text = "Log Out",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                ExpenseTextView(
                    text = "Are you sure you want to log out of your Vaulta account?",
                    fontSize = 14.sp,
                    color = LightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(ProfileUiEvent.OnConfirmLogout) },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ExpenseTextView(text = "Log Out", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ProfileUiEvent.OnDismissLogout) }) {
                    ExpenseTextView(text = "Cancel", color = LightGrey)
                }
            }
        )
    }

    if (uiState.showAutoDetectDisclosureDialog) {
        Dialog(
            onDismissRequest = { viewModel.onEvent(ProfileUiEvent.OnDismissAutoDetectDisclosure) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .wrapContentHeight()
                    .heightIn(max = 680.dp)
                    .shadow(24.dp, shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_notification),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            colorFilter = ColorFilter.tint(Zinc)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        ExpenseTextView(
                            text = "Auto-Detect Transactions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    ExpenseTextView(
                        text = "Vaulta can automatically detect and log your income and expenses when you receive bank & payment SMS text messages (via Google Messages or your default SMS app).",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Zinc.copy(alpha = 0.08f))
                            .border(1.dp, Zinc.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            ExpenseTextView(
                                text = "🔒 100% On-Device & Private (SMS Only):",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Zinc
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            ExpenseTextView(
                                text = "• Strictly SMS Only: Parses only incoming SMS text alerts from Google Messages or your default SMS app. Chat apps (WhatsApp, Telegram, Instagram, Discord, etc.) are never read.\n• 100% Local & Private: Raw SMS text is analyzed strictly in memory and is never saved, logged, or uploaded.\n• Noise & Scam Elimination: OTPs, security verification codes, ending balances, and personal chat texts are completely ignored.\n• Verified Bank Alerts: Supports standard Indian bank SMS formats (HDFC, ICICI, SBI, Axis, Kotak, etc.) and global payment SMS.",
                                fontSize = 11.5.sp,
                                color = LightGrey,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(12.dp)
                    ) {
                        ExpenseTextView(
                            text = "ℹ️ Device Setup & MIUI / HyperOS Notice:\n• Android 13+ sideload: Tap 'App Info & Restricted Settings' below, open 3-dots menu (top-right), and choose 'Allow restricted settings'.\n• Xiaomi / Redmi / POCO (MIUI & HyperOS): Enable 'Autostart' and select Battery Saver 'No restrictions' in App Info for uninterrupted tracking.",
                            fontSize = 11.sp,
                            color = LightGrey.copy(alpha = 0.85f),
                            lineHeight = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = { viewModel.onEvent(ProfileUiEvent.OnOpenNotificationSettings) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        ExpenseTextView(
                            text = "Open Notification Settings",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { viewModel.onEvent(ProfileUiEvent.OnOpenAppSettings) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Zinc.copy(alpha = 0.5f))
                    ) {
                        ExpenseTextView(
                            text = "App Info & Restricted Settings",
                            color = Zinc,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { viewModel.onEvent(ProfileUiEvent.OnDismissAutoDetectDisclosure) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExpenseTextView(
                            text = "Cancel",
                            color = LightGrey,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }

    if (uiState.showResetEverythingDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onEvent(ProfileUiEvent.OnDismissResetEverything) },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        colorFilter = ColorFilter.tint(Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ExpenseTextView(
                        text = "Reset Everything?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Red
                    )
                }
            },
            text = {
                Column {
                    ExpenseTextView(
                        text = "This will permanently erase all your transactions, reset your monthly budget to 0.0, and clear auto-detect settings.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ExpenseTextView(
                        text = "⚠️ This action cannot be undone. Are you absolutely sure you want to proceed?",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Red
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.onEvent(ProfileUiEvent.OnConfirmResetEverything) },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    ExpenseTextView(text = "Erase & Reset Everything", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onEvent(ProfileUiEvent.OnDismissResetEverything) }) {
                    ExpenseTextView(text = "Cancel", color = LightGrey)
                }
            }
        )
    }

    if (showCurrencyDialog) {
        CurrencySelectionDialog(
            currentCurrency = currentCurrency,
            onDismiss = { showCurrencyDialog = false },
            onCurrencySelected = { selected ->
                viewModel.onCurrencySelected(selected)
                showCurrencyDialog = false
            }
        )
    }

    if (uiState.isEditing) {
        EditProfileDialog(
            name = uiState.editName,
            email = uiState.editEmail,
            phone = uiState.editPhone,
            onNameChange = { viewModel.onEvent(ProfileUiEvent.OnNameChanged(it)) },
            onEmailChange = { viewModel.onEvent(ProfileUiEvent.OnEmailChanged(it)) },
            onPhoneChange = { viewModel.onEvent(ProfileUiEvent.OnPhoneChanged(it)) },
            onSave = { viewModel.onEvent(ProfileUiEvent.OnSaveEdit) },
            onCancel = { viewModel.onEvent(ProfileUiEvent.OnCancelEdit) }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (topBar, topBarContent, scrollContent) = createRefs()

            Image(
                painter = painterResource(id = R.drawable.ic_topbar),
                contentDescription = null,
                modifier = Modifier.constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }
            )

            // Top bar header with Back button, Title and Edit action
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 54.dp, start = 16.dp, end = 16.dp)
                    .constrainAs(topBarContent) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { navController.popBackStack() }
                )
                ExpenseTextView(
                    text = "Profile",
                    style = Typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable { viewModel.onEvent(ProfileUiEvent.OnStartEdit) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = "Edit Profile",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Red.copy(alpha = 0.35f))
                            .border(1.5.dp, Red.copy(alpha = 0.7f), CircleShape)
                            .clickable { viewModel.onEvent(ProfileUiEvent.OnLogoutClicked) },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logout),
                            contentDescription = "Log Out",
                            modifier = Modifier.size(18.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                }
            }

            // Scrollable Content with strict constraints for smooth top-to-bottom scrolling
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(scrollContent) {
                        top.linkTo(topBarContent.bottom, margin = 16.dp)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                        height = androidx.constraintlayout.compose.Dimension.fillToConstraints
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // Header Profile Info Banner
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExpenseTextView(
                        text = user?.name ?: "Vaulta User",
                        style = Typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val isGoogle = user?.authProvider?.contains("Google", ignoreCase = true) == true
                    val isGuest = user?.authProvider?.contains("Guest", ignoreCase = true) == true
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                when {
                                    isGoogle -> Color(0xFFE8F0FE)
                                    isGuest -> Color(0xFFFFF3E0)
                                    else -> Color(0xFFE8F5E9)
                                }
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Image(
                            painter = painterResource(
                                id = when {
                                    isGoogle -> R.drawable.ic_google
                                    isGuest -> R.drawable.ic_person
                                    else -> R.drawable.ic_check_circle
                                }
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            colorFilter = if (isGuest) ColorFilter.tint(Color(0xFFE65100)) else null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        ExpenseTextView(
                            text = user?.authProvider ?: "Supabase Auth (Verified)",
                            color = when {
                                isGoogle -> Color(0xFF1967D2)
                                isGuest -> Color(0xFFE65100)
                                else -> Color(0xFF2E7D32)
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (isGuest) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpenseTextView(
                            text = "Guest Mode: Running locally. Log in anytime to backup & sync.",
                            fontSize = 11.sp,
                            color = LightGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User Details Card (Name, Mobile Number, Email ID)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    ExpenseTextView(
                        text = "ACCOUNT DETAILS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Name / Username Row
                    ProfileDetailItem(
                        iconRes = R.drawable.ic_person,
                        label = "Username / Name",
                        value = user?.name?.ifBlank { "Not specified" } ?: "Not specified"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = Color(0xFFF0F0F0)
                    )

                    // Mobile Number Row
                    ProfileDetailItem(
                        iconRes = R.drawable.ic_phone,
                        label = "Mobile Number",
                        value = user?.phoneNumber ?: "Not specified"
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 14.dp),
                        color = Color(0xFFF0F0F0)
                    )

                    // Email ID Row
                    ProfileDetailItem(
                        iconRes = R.drawable.ic_mail,
                        label = "Email Address",
                        value = user?.email ?: "Not specified"
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Activity / Expenses Summary Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    ExpenseTextView(
                        text = "ACTIVITY SUMMARY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            ExpenseTextView(
                                text = "Total Transactions",
                                fontSize = 13.sp,
                                color = LightGrey
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpenseTextView(
                                text = "${expenses.size}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Zinc
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            ExpenseTextView(
                                text = "Account Status",
                                fontSize = 13.sp,
                                color = LightGrey
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpenseTextView(
                                text = "Active",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                
                // Currency & Region Preference Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    ExpenseTextView(
                        text = "CURRENCY & REGION",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseTextView(
                        text = "Choose your preferred currency symbol ($, ₹, £, ﷼, €, etc.)",
                        fontSize = 13.sp,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { showCurrencyDialog = true }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Zinc.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            ExpenseTextView(
                                text = currentCurrency.symbol,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Zinc
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            ExpenseTextView(
                                text = "${currentCurrency.flag} ${currentCurrency.code} — ${currentCurrency.name}",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            ExpenseTextView(
                                text = "Symbol: ${currentCurrency.symbol} • ${currentCurrency.country}",
                                fontSize = 12.sp,
                                color = LightGrey
                            )
                        }

                        Box(
                            modifier = Modifier
                                .background(
                                    color = Zinc,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            ExpenseTextView(
                                text = "Change",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Auto-Detect Transactions Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpenseTextView(
                            text = "AUTO-DETECT TRANSACTIONS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightGrey,
                            letterSpacing = 1.sp
                        )

                        if (isAutoDetectEnabled) {
                            val badgeBg = if (isServiceConnected) Color(0xFFE8F5E9) else if (isNotificationAccessGranted) Color(0xFFFFF3E0) else Color(0xFFEEEEEE)
                            val dotColor = if (isServiceConnected) Color(0xFF2E7D32) else if (isNotificationAccessGranted) Color(0xFFE65100) else Color(0xFF757575)
                            val badgeText = if (isServiceConnected) "Active & Connected" else if (isNotificationAccessGranted) "Permission Granted" else "Access Required"

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(badgeBg)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                ExpenseTextView(
                                    text = badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = dotColor
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 12.dp)
                        ) {
                            ExpenseTextView(
                                text = "Smart Payment Alerts",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ExpenseTextView(
                                text = "Automatically log income & expenses from bank & payment SMS text alerts",
                                fontSize = 13.sp,
                                color = LightGrey,
                                lineHeight = 18.sp
                            )
                        }

                        androidx.compose.material3.Switch(
                            checked = isAutoDetectEnabled,
                            onCheckedChange = { checked ->
                                viewModel.onEvent(ProfileUiEvent.OnToggleAutoDetect(checked))
                            },
                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Zinc,
                                uncheckedThumbColor = LightGrey,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )
                    }

                    if (isAutoDetectEnabled) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFFF0F0F0)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // MIUI / Xiaomi / OEM Troubleshooting tip
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { viewModel.onEvent(ProfileUiEvent.OnOpenAppSettings) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ExpenseTextView(text = "⚡", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            ExpenseTextView(
                                text = "Xiaomi / Redmi / POCO (MIUI & HyperOS): Enable 'Autostart' and set Battery Saver to 'No restrictions' in App Info for uninterrupted tracking.",
                                fontSize = 11.5.sp,
                                color = Zinc,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Test Auto-Detect Button
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ProfileUiEvent.OnTestAutoDetect) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Zinc.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Zinc)
                        ) {
                            ExpenseTextView(text = "🧪", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            ExpenseTextView(
                                text = "Test Auto-Detect (Simulate Sample SMS)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Zinc
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // App Appearance / Theme Mode Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    ExpenseTextView(
                        text = "APP APPEARANCE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseTextView(
                        text = "Customize dark & light mode across the app",
                        fontSize = 13.sp,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemeOptionButton(
                            title = "Light",
                            icon = "☀️",
                            isSelected = currentThemeMode == "LIGHT",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEvent(ProfileUiEvent.OnThemeChanged("LIGHT")) }
                        )
                        ThemeOptionButton(
                            title = "Dark",
                            icon = "🌙",
                            isSelected = currentThemeMode == "DARK",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEvent(ProfileUiEvent.OnThemeChanged("DARK")) }
                        )
                        ThemeOptionButton(
                            title = "System",
                            icon = "⚙️",
                            isSelected = currentThemeMode == "SYSTEM",
                            modifier = Modifier.weight(1f),
                            onClick = { viewModel.onEvent(ProfileUiEvent.OnThemeChanged("SYSTEM")) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Edit Profile Button
                OutlinedButton(
                    onClick = { viewModel.onEvent(ProfileUiEvent.OnStartEdit) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Zinc)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_edit),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        ExpenseTextView(
                            text = "Edit Profile Information",
                            color = Zinc,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // DATA & PRIVACY DANGER ZONE CARD
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        ExpenseTextView(
                            text = "DATA & PRIVACY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = LightGrey,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ExpenseTextView(
                            text = "Reset all stored transactions, budgets, and configurations.",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.onEvent(ProfileUiEvent.OnRequestResetEverything) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                            border = BorderStroke(1.dp, Red.copy(alpha = 0.7f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = "Reset Everything",
                                    modifier = Modifier.size(18.dp),
                                    colorFilter = ColorFilter.tint(Red)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ExpenseTextView(
                                    text = "Reset Everything",
                                    color = Red,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Prominent Solid Red Logout Button
                Button(
                    onClick = { viewModel.onEvent(ProfileUiEvent.OnLogoutClicked) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logout),
                            contentDescription = "Log Out",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        ExpenseTextView(
                            text = "Log Out from Vaulta",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Professional Developer Branding Footer
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExpenseTextView(
                        text = "from",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = LightGrey,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ExpenseTextView(
                        text = "winblader.org",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    ExpenseTextView(
                        text = "Vaulta v${com.waleed.vaulta.android.BuildConfig.VERSION_NAME} • Smart Expense Tracker",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = LightGrey.copy(alpha = 0.8f)
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileDetailItem(
    iconRes: Int,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Zinc.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            ExpenseTextView(
                text = label,
                fontSize = 12.sp,
                color = LightGrey,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            ExpenseTextView(
                text = value,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun EditProfileDialog(
    name: String,
    email: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            ExpenseTextView(
                text = "Edit Profile",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                ExpenseTextView(text = "FULL NAME", fontSize = 11.sp, color = LightGrey)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExpenseTextView(text = "MOBILE NUMBER", fontSize = 11.sp, color = LightGrey)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                )

                Spacer(modifier = Modifier.height(12.dp))

                ExpenseTextView(text = "EMAIL ADDRESS", fontSize = 11.sp, color = LightGrey)
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Zinc)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Zinc),
                shape = RoundedCornerShape(8.dp)
            ) {
                ExpenseTextView(text = "Save Changes", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                ExpenseTextView(text = "Cancel", color = LightGrey)
            }
        }
    )
}

@Composable
fun ThemeOptionButton(
    title: String,
    icon: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ExpenseTextView(text = icon, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(4.dp))
            ExpenseTextView(
                text = title,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}



@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CurrencySelectionDialog(
    currentCurrency: CurrencyItem,
    onDismiss: () -> Unit,
    onCurrencySelected: (CurrencyItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val categories = listOf("All", "Dollar", "Rupee", "Pound", "Riyal", "Euro", "Dirham", "Dinar", "Other")

    val filteredList = CurrencyManager.supportedCurrencies.filter { currency ->
        val matchesCategory = selectedCategory == "All" || currency.category.equals(selectedCategory, ignoreCase = true)
        val query = searchQuery.trim().lowercase()
        val matchesSearch = query.isEmpty() ||
                currency.name.lowercase().contains(query) ||
                currency.code.lowercase().contains(query) ||
                currency.symbol.lowercase().contains(query) ||
                currency.country.lowercase().contains(query)
        matchesCategory && matchesSearch
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(620.dp)
                .shadow(24.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Dialog Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        ExpenseTextView(
                            text = "Choose Currency",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpenseTextView(
                            text = "Select your preferred currency symbol",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        ExpenseTextView(
                            text = "Cancel",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Zinc
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Search Box
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search currency, dollar, rupee, riyal...",
                            fontSize = 13.sp,
                            color = LightGrey
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Zinc,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Category Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedCategory == cat) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Zinc,
                                selectedLabelColor = Color.White,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(6.dp))

                // List of Currencies
                if (filteredList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpenseTextView(
                            text = "No currencies matching '$searchQuery'",
                            fontSize = 14.sp,
                            color = LightGrey
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredList, key = { it.code }) { item ->
                            val isSelected = item.code == currentCurrency.code
                            CurrencyListItem(
                                item = item,
                                isSelected = isSelected,
                                onSelect = { onCurrencySelected(item) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CurrencyListItem(
    item: CurrencyItem,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val borderColor = if (isSelected) Zinc else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val backgroundColor = if (isSelected) Zinc.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable { onSelect() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Flag
        Text(text = item.flag, fontSize = 24.sp)

        Spacer(modifier = Modifier.width(12.dp))

        // Details
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpenseTextView(
                    text = item.code,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                // Symbol pill
                Box(
                    modifier = Modifier
                        .background(Zinc.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    ExpenseTextView(
                        text = item.symbol,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Zinc
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            ExpenseTextView(
                text = "${item.name} • ${item.country}",
                fontSize = 12.sp,
                color = LightGrey
            )
        }

        // Live preview of sample amount
        Column(horizontalAlignment = Alignment.End) {
            ExpenseTextView(
                text = CurrencyManager.formatAmount(1250.0, item),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isSelected) Zinc else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(
                    selectedColor = Zinc,
                    unselectedColor = Color(0xFFCCCCCC)
                ),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
