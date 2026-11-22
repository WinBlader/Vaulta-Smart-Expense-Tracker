@file:OptIn(ExperimentalMaterial3Api::class)

package com.waleed.vaulta.android.feature.transactionlist

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.base.NavigationEvent
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.feature.home.TransactionItem
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.utils.ReceiptExportManager
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun TransactionListScreen(
    navController: NavController,
    viewModel: TransactionListViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val currentCurrency by CurrencyManager.selectedCurrency.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showCustomDatePicker by remember { mutableStateOf(false) }
    var selectedItemForReceipt by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showExportPdfDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            if (event is NavigationEvent.NavigateBack) {
                navController.popBackStack()
            }
        }
    }

    // Receipt Detail & Share Dialog
    selectedItemForReceipt?.let { item ->
        TransactionReceiptDialog(
            item = item,
            onDelete = { viewModel.onEvent(TransactionListUiEvent.OnDeleteTransaction(it)) },
            onDismiss = { selectedItemForReceipt = null }
        )
    }

    // Custom Date Range Picker Dialog
    if (showCustomDatePicker) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showCustomDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis ?: start
                        if (start != null && end != null) {
                            viewModel.onEvent(TransactionListUiEvent.OnCustomDateRangeSelected(start, end))
                        }
                        showCustomDatePicker = false
                    }
                ) {
                    Text("Apply Range", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDatePicker = false }) {
                    Text("Cancel", color = LightGrey)
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = {
                    Text(
                        text = "Custom Date Range",
                        modifier = Modifier.padding(start = 24.dp, top = 16.dp),
                        fontWeight = FontWeight.Bold
                    )
                },
                headline = {
                    Text(
                        text = "Select Start and End Dates",
                        modifier = Modifier.padding(start = 24.dp, bottom = 12.dp),
                        fontSize = 13.sp,
                        color = LightGrey
                    )
                },
                showModeToggle = false,
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Export Statement PDF Dialog (Direct Download & Share)
    if (showExportPdfDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showExportPdfDialog = false },
            title = {
                Text(
                    text = "Export Financial Statement",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Period: ${uiState.dateRange} • ${uiState.transactions.size} records",
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Net Balance: ${Utils.formatCurrency(uiState.netBalance)}",
                        fontSize = 13.sp,
                        color = if (uiState.netBalance >= 0) Green else Red,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Choose whether to directly save the statement PDF to your device's Downloads/Vaulta folder or share it via the Android share sheet.",
                        fontSize = 12.sp,
                        color = LightGrey,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Direct Download PDF Button
                    Button(
                        onClick = {
                            showExportPdfDialog = false
                            ReceiptExportManager.downloadTransactionsStatementPdf(
                                context = context,
                                transactions = uiState.transactions,
                                filterType = uiState.filterType,
                                dateRange = uiState.dateRange,
                                totalIncome = uiState.totalIncome,
                                totalExpense = uiState.totalExpense,
                                netBalance = uiState.netBalance
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("📥 Download", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }

                    // Share PDF Button
                    OutlinedButton(
                        onClick = {
                            showExportPdfDialog = false
                            ReceiptExportManager.exportTransactionsStatementPdf(
                                context = context,
                                transactions = uiState.transactions,
                                filterType = uiState.filterType,
                                dateRange = uiState.dateRange,
                                totalIncome = uiState.totalIncome,
                                totalExpense = uiState.totalExpense,
                                netBalance = uiState.netBalance
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Share PDF", fontSize = 12.sp)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportPdfDialog = false }) {
                    Text("Cancel", color = LightGrey)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Back Button
                    Image(
                        painter = painterResource(id = R.drawable.ic_back),
                        contentDescription = "Back",
                        modifier = Modifier
                            .size(24.dp)
                            .clickable { navController.popBackStack() },
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )

                    // Screen Title
                    ExpenseTextView(
                        text = "Transactions & History",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Right Actions: Export PDF & Filter Toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Export PDF Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable {
                                    showExportPdfDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "📄 PDF",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Filter Menu Toggle
                        Image(
                            painter = painterResource(id = R.drawable.ic_filter),
                            contentDescription = "Filter",
                            modifier = Modifier
                                .size(22.dp)
                                .clickable { showFilterSheet = !showFilterSheet },
                            colorFilter = ColorFilter.tint(
                                if (uiState.isFilterActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Instant Search Bar
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = {
                        viewModel.onEvent(TransactionListUiEvent.OnSearchQueryChanged(it))
                    },
                    placeholder = {
                        Text(text = "Search by title, category, or amount...", fontSize = 13.sp, color = LightGrey)
                    },
                    leadingIcon = {
                        Text(text = "🔍", fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp, end = 4.dp))
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            Text(
                                text = "✕",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightGrey,
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable {
                                        viewModel.onEvent(TransactionListUiEvent.OnSearchQueryChanged(""))
                                    }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )

                // Expandable Filter Section
                AnimatedVisibility(
                    visible = showFilterSheet || uiState.isFilterActive,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        // Type Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Type:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightGrey
                            )
                            listOf("All", "Expense", "Income").forEach { type ->
                                val isSelected = uiState.filterType == type
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.onEvent(TransactionListUiEvent.OnFilterTypeChanged(type))
                                    },
                                    label = { Text(type, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Date Range Chips Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Period:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightGrey
                            )
                            listOf("All Time", "Today", "This Week", "This Month", "Last Month").forEach { range ->
                                val isSelected = uiState.dateRange == range
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.onEvent(TransactionListUiEvent.OnDateRangeChanged(range))
                                    },
                                    label = { Text(range, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                            // Custom Date Range Chip
                            FilterChip(
                                selected = uiState.dateRange == "Custom",
                                onClick = { showCustomDatePicker = true },
                                label = { Text("📅 Custom Range", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }

                        // Category Filter Row
                        if (uiState.availableCategories.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Category:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LightGrey
                                )
                                uiState.availableCategories.forEach { category ->
                                    val isSelected = uiState.selectedCategory == category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.onEvent(TransactionListUiEvent.OnCategoryChanged(category))
                                        },
                                        label = { Text(category, fontSize = 12.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Summary KPI Strip Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Income Metric
                            Column {
                                Text(text = "INCOME", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = "+${Utils.formatCurrency(uiState.totalIncome, currentCurrency)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Green
                                )
                            }

                            // Expense Metric
                            Column {
                                Text(text = "EXPENSE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = "-${Utils.formatCurrency(uiState.totalExpense, currentCurrency)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Red
                                )
                            }

                            // Net Metric
                            Column {
                                Text(text = "NET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = Utils.formatCurrency(uiState.netBalance, currentCurrency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.netBalance >= 0) MaterialTheme.colorScheme.primary else Red
                                )
                            }
                        }

                        // Active Filter Notice & Reset Button
                        if (uiState.isFilterActive) {
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Showing ${uiState.transactions.size} filtered record(s)",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )

                                Text(
                                    text = "✕ Reset Filters",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Red,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            viewModel.onEvent(TransactionListUiEvent.OnResetFilters)
                                        }
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_stats),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            colorFilter = ColorFilter.tint(LightGrey)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.isFilterActive) "No transactions match your current filters." else "No transactions recorded yet.",
                            color = LightGrey,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        if (uiState.isFilterActive) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.onEvent(TransactionListUiEvent.OnResetFilters) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear All Filters", color = Color.White)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap any transaction to view details & share receipt (PDF / PNG)",
                            fontSize = 11.sp,
                            color = LightGrey.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    items(
                        items = uiState.transactions,
                        key = { it.id ?: 0 }
                    ) { item ->
                        val currentItem by rememberUpdatedState(item)
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                    viewModel.onEvent(TransactionListUiEvent.OnDeleteTransaction(currentItem))
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
                                    val formattedAmount = Utils.formatCurrency(item.amount, currentCurrency)

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedItemForReceipt = item
                                            }
                                    ) {
                                        TransactionItem(
                                            title = item.title,
                                            amount = if (item.type.equals("Income", ignoreCase = true)) "+$formattedAmount" else "-$formattedAmount",
                                            icon = icon,
                                            date = Utils.formatStringDateToMonthDayYear(item.date),
                                            color = if (item.type.equals("Income", ignoreCase = true)) Green else Red,
                                            modifier = Modifier
                                        )
                                    }
                                }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
