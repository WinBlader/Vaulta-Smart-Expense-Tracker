@file:OptIn(ExperimentalMaterial3Api::class)

package com.waleed.vaulta.android.feature.home

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.waleed.vaulta.android.data.CurrencyManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.feature.budget.SetBudgetDialog
import com.waleed.vaulta.android.feature.transactionlist.TransactionReceiptDialog
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.NavRouts
import com.waleed.vaulta.android.widget.ExpenseTextView
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.base.HomeNavigationEvent
import com.waleed.vaulta.android.base.NavigationEvent
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Typography
import com.waleed.vaulta.android.utils.Utils


@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                NavigationEvent.NavigateBack -> navController.popBackStack()
                HomeNavigationEvent.NavigateToSeeAll -> {
                    navController.navigate("/all_transactions")
                }

                HomeNavigationEvent.NavigateToAddIncome -> {
                    navController.navigate("/add_income")
                }

                HomeNavigationEvent.NavigateToAddExpense -> {
                    navController.navigate("/add_exp")
                }

                HomeNavigationEvent.NavigateToProfile -> {
                    navController.navigate("/profile")
                }

                HomeNavigationEvent.NavigateToSpendsDashboard -> {
                    navController.navigate(NavRouts.spends_dashboard)
                }

                else -> {}
            }
        }
    }

    val userState by viewModel.user.collectAsState()
    val currentCurrency by com.waleed.vaulta.android.data.CurrencyManager.selectedCurrency.collectAsState()
    val greetingName = userState?.name?.ifEmpty { "User" } ?: "User"
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAddCreditOweDialog by remember { mutableStateOf(false) }
    var selectedItemForReceipt by remember { mutableStateOf<ExpenseEntity?>(null) }

    if (showAddCreditOweDialog) {
        AddCreditOweDialog(
            onDismiss = { showAddCreditOweDialog = false },
            onSave = {
                viewModel.insertExpense(it)
                showAddCreditOweDialog = false
            }
        )
    }

    // Receipt Detail, Edit & Share Dialog
    selectedItemForReceipt?.let { item ->
        TransactionReceiptDialog(
            item = item,
            onEdit = { viewModel.updateExpense(it) },
            onDelete = { viewModel.deleteExpense(it) },
            onDismiss = { selectedItemForReceipt = null }
        )
    }

    if (showFeedbackDialog) {
        FeedbackSupportDialog(onDismiss = { showFeedbackDialog = false })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (nameRow, list, card, topBar, add) = createRefs()
            Image(
                painter = painterResource(id = R.drawable.ic_topbar), contentDescription = null,
                modifier = Modifier.constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp)
                    .constrainAs(nameRow) {
                        top.linkTo(parent.top)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                    }) {
                Column(modifier = Modifier.align(Alignment.CenterStart)) {
                    ExpenseTextView(
                        text = "Hi, $greetingName",
                        style = Typography.headlineMedium,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    ExpenseTextView(
                        text = "Welcome to Vaulta",
                        style = Typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Customer Feedback & Support Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                showFeedbackDialog = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_support),
                            contentDescription = "Customer Support & Feedback",
                            modifier = Modifier.size(20.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }

                    // User Profile Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                            .clickable {
                                viewModel.onEvent(HomeUiEvent.OnProfileClicked)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_profile),
                            contentDescription = "Profile",
                            modifier = Modifier.size(22.dp),
                            colorFilter = ColorFilter.tint(Color.White)
                        )
                    }
                }
            }

            val currentCurrency = CurrencyManager.selectedCurrency.collectAsState().value
            val state = viewModel.expenses.collectAsState(initial = emptyList())
            val monthlyBudget by viewModel.monthlyBudget.collectAsState()
            val currentSpends = viewModel.getCurrentMonthSpends(state.value)
            val expense = viewModel.getTotalExpense(state.value)
            val income = viewModel.getTotalIncome(state.value)
            val balance = viewModel.getBalance(state.value)

            var showBudgetDialog by remember { mutableStateOf(false) }

            // Auto-check budget and negative balance alerts when expenses change
            LaunchedEffect(state.value, monthlyBudget) {
                viewModel.checkBudgetAndNotify(state.value)
                viewModel.checkBalanceAndNotify(state.value)
            }

            if (showBudgetDialog) {
                SetBudgetDialog(
                    currentBudget = monthlyBudget,
                    onSave = { newBudget ->
                        viewModel.setMonthlyBudget(newBudget, state.value)
                        showBudgetDialog = false
                    },
                    onDismiss = { showBudgetDialog = false }
                )
            }

            CardItem(
                modifier = Modifier.constrainAs(card) {
                    top.linkTo(nameRow.bottom)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                },
                balance = balance, income = income, expense = expense
            )
            TransactionList(
                modifier = Modifier
                    .fillMaxWidth()
                    .constrainAs(list) {
                        top.linkTo(card.bottom)
                        start.linkTo(parent.start)
                        end.linkTo(parent.end)
                        bottom.linkTo(parent.bottom)
                        height = Dimension.fillToConstraints
                    },
                list = state.value,
                spendsThisMonth = currentSpends,
                monthlyBudget = monthlyBudget,
                onSetBudgetClicked = { showBudgetDialog = true },
                onSpendsDashboardClicked = { viewModel.onEvent(HomeUiEvent.OnSpendsDashboardClicked) },
                onItemClicked = { selectedItemForReceipt = it },
                onDeleteClicked = { viewModel.deleteExpense(it) },
                onSeeAllClicked = {
                    viewModel.onEvent(HomeUiEvent.OnSeeAllClicked)
                }
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .constrainAs(add) {
                        bottom.linkTo(parent.bottom)
                        end.linkTo(parent.end)
                    }, contentAlignment = Alignment.BottomEnd
            ) {
                MultiFloatingActionButton(
                    modifier = Modifier,
                    onAddExpenseClicked = {
                        viewModel.onEvent(HomeUiEvent.OnAddExpenseClicked)
                    },
                    onAddIncomeClicked = {
                        viewModel.onEvent(HomeUiEvent.OnAddIncomeClicked)
                    },
                    onAddCreditOweClicked = {
                        showAddCreditOweDialog = true
                    }
                )
            }
        }
    }
}

@Composable
fun MultiFloatingActionButton(
    modifier: Modifier,
    onAddExpenseClicked: () -> Unit,
    onAddIncomeClicked: () -> Unit,
    onAddCreditOweClicked: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Secondary FABs
            AnimatedVisibility(visible = expanded) {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(16.dp)) {
                    // Credit / Owe FAB (Yellow)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Color(0xFFF59E0B), shape = RoundedCornerShape(12.dp))
                            .clickable {
                                expanded = false
                                onAddCreditOweClicked.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_credit_owe),
                            contentDescription = "Credit & Owe",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Color(0xFF2E7D32), shape = RoundedCornerShape(12.dp))
                            .clickable {
                                expanded = false
                                onAddIncomeClicked.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_income),
                            contentDescription = "Add Income",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(color = Color(0xFFD32F2F), shape = RoundedCornerShape(12.dp))
                            .clickable {
                                expanded = false
                                onAddExpenseClicked.invoke()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_expense),
                            contentDescription = "Add Expense",
                            tint = Color.White
                        )
                    }
                }
            }
            // Main FAB
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(color = Zinc)
                    .clickable {
                        expanded = !expanded
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_addbutton),
                    contentDescription = "small floating action button",
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

@Composable
fun CardItem(
    modifier: Modifier,
    balance: String, income: String, expense: String
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Zinc)
            .padding(16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column {
                ExpenseTextView(
                    text = "Total Balance",
                    style = Typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.size(8.dp))
                ExpenseTextView(
                    text = balance, style = Typography.headlineLarge, color = Color.White,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CardRowItem(
                modifier = Modifier
                    .align(Alignment.CenterStart),
                title = "Income",
                amount = income,
                imaget = R.drawable.ic_income
            )
            Spacer(modifier = Modifier.size(8.dp))
            CardRowItem(
                modifier = Modifier
                    .align(Alignment.CenterEnd),
                title = "Expense",
                amount = expense,
                imaget = R.drawable.ic_expense
            )
        }

    }
}


@Composable
fun TransactionList(
    modifier: Modifier,
    list: List<ExpenseEntity>,
    title: String = "Recent Transactions",
    spendsThisMonth: Double = 0.0,
    monthlyBudget: Double = 0.0,
    onSetBudgetClicked: (() -> Unit)? = null,
    onSpendsDashboardClicked: (() -> Unit)? = null,
    onItemClicked: ((ExpenseEntity) -> Unit)? = null,
    onDeleteClicked: ((ExpenseEntity) -> Unit)? = null,
    onSeeAllClicked: () -> Unit
) {
    val currentCurrency = CurrencyManager.selectedCurrency.collectAsState().value
    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                if (title == "Recent Transactions") {
                    // BHIM UPI style Spends & Budget card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSpendsDashboardClicked?.invoke() },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Spends this month",
                                        fontSize = 12.sp,
                                        color = LightGrey,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Utils.formatCurrency(spendsThisMonth),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                // Orange/Coral Pill Button (BHIM UPI style)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color(0xFFE67E22))
                                        .clickable { onSetBudgetClicked?.invoke() }
                                        .padding(horizontal = 14.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = if (monthlyBudget > 0) "Edit Budget" else "Set Budget",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (monthlyBudget > 0.0) {
                                val percentUsed = ((spendsThisMonth / monthlyBudget) * 100).toInt()
                                val isExceeded = spendsThisMonth >= monthlyBudget
                                val isApproaching = percentUsed >= 80 && !isExceeded

                                Spacer(modifier = Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { (spendsThisMonth / monthlyBudget).toFloat().coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = when {
                                        isExceeded -> Red
                                        isApproaching -> Color(0xFFE67E22)
                                        else -> Green
                                    },
                                    trackColor = Color.Black.copy(alpha = 0.08f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "$percentUsed% of ${Utils.formatCurrency(monthlyBudget)}",
                                        fontSize = 13.sp,
                                        color = LightGrey
                                    )
                                    Text(
                                        text = if (isExceeded) "⚠️ Budget Exceeded" else "${Utils.formatCurrency((monthlyBudget - spendsThisMonth).coerceAtLeast(0.0))} left",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExceeded) Red else Green
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Spends Dashboard →",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Box(modifier = modifier.fillMaxWidth()) {
                    ExpenseTextView(
                        text = title,
                        style = Typography.titleLarge,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground
                    )
                    if (title == "Recent Transactions") {
                        ExpenseTextView(
                            text = "See all",
                            style = Typography.bodyMedium,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .clickable {
                                    onSeeAllClicked.invoke()
                                }
                        )
                    }
                }
                Spacer(modifier = Modifier.size(12.dp))
            }
        }
        items(
            items = list,
            key = { item -> item.id ?: 0 }
        ) { item ->
            val currentItem by rememberUpdatedState(item)
            val dismissState = rememberSwipeToDismissBoxState(
                confirmValueChange = { dismissValue ->
                    if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                        onDeleteClicked?.invoke(currentItem)
                        true
                    } else false
                }
            )

            SwipeToDismissBox(
                state = dismissState,
                backgroundContent = {
                    val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Red)
                            .padding(horizontal = 20.dp),
                        contentAlignment = alignment
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                content = {
                    Surface(
                        color = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val icon = Utils.getItemIcon(item)
                        val isIncomeLike = item.type == "Income" || item.type == "Owed to You" || item.type == "Credit"
                        val isCreditOwe = item.type == "Owed to You" || item.type == "You Owe"
                        val amount = if (isIncomeLike) item.amount else item.amount * -1
                        val itemColor = when {
                            isCreditOwe -> Color(0xFFF59E0B)
                            isIncomeLike -> Green
                            else -> Red
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onItemClicked?.invoke(item) }
                        ) {
                            TransactionItem(
                                title = item.title,
                                amount = Utils.formatCurrency(amount, currentCurrency),
                                icon = icon,
                                date = Utils.formatStringDateToMonthDayYear(item.date),
                                color = itemColor,
                                note = item.note,
                                badgeText = if (isCreditOwe) item.type else null,
                                modifier = Modifier
                            )
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun TransactionItem(
    title: String,
    amount: String,
    icon: Int,
    date: String,
    color: Color,
    note: String? = null,
    badgeText: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(51.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ExpenseTextView(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!badgeText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF59E0B),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (!note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                ExpenseTextView(
                    text = "📝 $note",
                    fontSize = 12.sp,
                    color = LightGrey,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            ExpenseTextView(
                text = date,
                fontSize = 13.sp,
                color = LightGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        ExpenseTextView(
            text = amount,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}

@Composable
fun CardRowItem(modifier: Modifier, title: String, amount: String, imaget: Int) {
    Column(modifier = modifier) {
        Row {

            Image(
                painter = painterResource(id = imaget),
                contentDescription = null,
            )
            Spacer(modifier = Modifier.size(8.dp))
            ExpenseTextView(text = title, style = Typography.bodyLarge, color = Color.White)
        }
        Spacer(modifier = Modifier.size(4.dp))
        ExpenseTextView(text = amount, style = Typography.titleLarge, color = Color.White)
    }
}

@Preview(showBackground = true)
@Composable
fun HomeScreenPreview() {
    HomeScreen(rememberNavController())
}