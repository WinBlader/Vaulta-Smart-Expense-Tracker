@file:OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.waleed.vaulta.android.feature.transactionlist

import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.Icon
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waleed.vaulta.android.ui.theme.Zinc
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

sealed class DeleteRecordTarget {
    data class Transaction(val item: ExpenseEntity) : DeleteRecordTarget()
    data class CreditOwe(val id: String) : DeleteRecordTarget()
    data class SharedSplit(val id: String) : DeleteRecordTarget()
}

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
    var actionMenuItem by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var editingCreditOwe by remember { mutableStateOf<com.waleed.vaulta.android.data.model.CreditOweEntity?>(null) }

    editingCreditOwe?.let { co ->
        com.waleed.vaulta.android.feature.home.AddCreditOweDialog(
            initialEntity = co,
            onDismiss = { editingCreditOwe = null },
            onLookupContact = { query -> viewModel.lookupVaultaContact(query) },
            onSearchUsers = { query -> viewModel.searchVaultaUsers(query) },
            onSettle = {
                viewModel.settleCreditOwe(co.id)
                editingCreditOwe = null
            },
            onSave = { personName, amount, type, note, contact, counterpartyId ->
                val updated = co.copy(
                    personName = personName,
                    amount = amount,
                    type = type,
                    note = note,
                    counterpartyContact = contact,
                    counterpartyId = counterpartyId
                )
                viewModel.updateCreditOwe(updated)
                editingCreditOwe = null
            }
        )
    }

    var recordToDelete by remember { mutableStateOf<DeleteRecordTarget?>(null) }

    actionMenuItem?.let { item ->
        TransactionActionMenuBottomSheet(
            item = item,
            onEdit = {
                selectedItemForReceipt = item
                actionMenuItem = null
            },
            onDelete = {
                recordToDelete = DeleteRecordTarget.Transaction(item)
                actionMenuItem = null
            },
            onDismiss = { actionMenuItem = null }
        )
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            if (event is NavigationEvent.NavigateBack) {
                navController.popBackStack()
            }
        }
    }

    // Receipt Detail, Edit & Share Dialog
    selectedItemForReceipt?.let { item ->
        TransactionReceiptDialog(
            item = item,
            onEdit = { viewModel.onEvent(TransactionListUiEvent.OnUpdateTransaction(it)) },
            onDelete = {
                recordToDelete = DeleteRecordTarget.Transaction(it)
                selectedItemForReceipt = null
            },
            onDismiss = { selectedItemForReceipt = null }
        )
    }

    if (recordToDelete != null) {
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = {
                Text(
                    text = "Delete Record?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Red
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete this record?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        when (val target = recordToDelete) {
                            is DeleteRecordTarget.Transaction -> viewModel.onEvent(TransactionListUiEvent.OnDeleteTransaction(target.item))
                            is DeleteRecordTarget.CreditOwe -> viewModel.deleteCreditOwe(target.id)
                            is DeleteRecordTarget.SharedSplit -> viewModel.deleteSharedSplit(target.id)
                            null -> {}
                        }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Delete",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { recordToDelete = null }
                ) {
                    Text(text = "Cancel", color = LightGrey)
                }
            }
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

    // Export Statement Dialog (PDF & PNG - Download & Share)
    if (showExportDialog) {
        Dialog(
            onDismissRequest = { showExportDialog = false },
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
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            ExpenseTextView(
                                text = "Export Statement",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            ExpenseTextView(
                                text = "Period: ${uiState.dateRange} • ${uiState.transactions.size} records",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        TextButton(onClick = { showExportDialog = false }) {
                            ExpenseTextView(text = "✕", fontSize = 16.sp, color = LightGrey, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Summary box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                ExpenseTextView(text = "Total Income", fontSize = 13.sp, color = LightGrey)
                                ExpenseTextView(text = "+${Utils.formatCurrency(uiState.totalIncome)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Green)
                            }
                            Column {
                                ExpenseTextView(text = "Total Expense", fontSize = 13.sp, color = LightGrey)
                                ExpenseTextView(text = "-${Utils.formatCurrency(uiState.totalExpense)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Red)
                            }
                            Column {
                                ExpenseTextView(text = "Net Balance", fontSize = 13.sp, color = LightGrey)
                                ExpenseTextView(
                                    text = Utils.formatCurrency(uiState.netBalance),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.netBalance >= 0) Green else Red
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 1: PDF Format
                    ExpenseTextView(
                        text = "PDF DOCUMENT STATEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showExportDialog = false
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
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Download PDF", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
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
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Text("Share PDF", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section 2: PNG Format
                    ExpenseTextView(
                        text = "PNG IMAGE STATEMENT",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightGrey,
                        letterSpacing = 1.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                showExportDialog = false
                                ReceiptExportManager.downloadTransactionsStatementPng(
                                    context = context,
                                    transactions = uiState.transactions,
                                    filterType = uiState.filterType,
                                    dateRange = uiState.dateRange,
                                    totalIncome = uiState.totalIncome,
                                    totalExpense = uiState.totalExpense,
                                    netBalance = uiState.netBalance
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Zinc)
                        ) {
                            Text("Download PNG", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                        }

                        OutlinedButton(
                            onClick = {
                                showExportDialog = false
                                ReceiptExportManager.exportTransactionsStatementPng(
                                    context = context,
                                    transactions = uiState.transactions,
                                    filterType = uiState.filterType,
                                    dateRange = uiState.dateRange,
                                    totalIncome = uiState.totalIncome,
                                    totalExpense = uiState.totalExpense,
                                    netBalance = uiState.netBalance
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Zinc.copy(alpha = 0.5f))
                        ) {
                            Text("Share PNG", fontSize = 12.sp, color = Zinc, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    TextButton(
                        onClick = { showExportDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ExpenseTextView(text = "Close", color = LightGrey, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    val currentUser by viewModel.currentUser.collectAsState()
    val currentUserId = currentUser?.id ?: ""
    val currentUserEmail = currentUser?.email ?: ""
    val currentUserPhone = currentUser?.phoneNumber ?: ""

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
                        // Export (PDF & PNG) Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                .clickable {
                                    showExportDialog = true
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Export",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "PDF/PNG",
                                    fontSize = 9.sp,
                                    color = LightGrey
                                )
                            }
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
                            listOf("All", "Expense", "Income", "Credit / Owe", "Shared Splits").forEach { type ->
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
                                Text(text = "INCOME", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = "+${Utils.formatCurrency(uiState.totalIncome, currentCurrency)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Green
                                )
                            }

                            // Expense Metric
                            Column {
                                Text(text = "EXPENSE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = "-${Utils.formatCurrency(uiState.totalExpense, currentCurrency)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Red
                                )
                            }

                            // Net Metric
                            Column {
                                Text(text = "NET", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LightGrey)
                                Text(
                                    text = Utils.formatCurrency(uiState.netBalance, currentCurrency),
                                    fontSize = 15.sp,
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
            if (uiState.filterType == "Shared Splits") {
                if (uiState.sharedSplits.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_credit_owe),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = LightGrey
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No shared splits found.",
                                color = LightGrey,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    val splitGroups = remember(uiState.sharedSplits) {
                        uiState.sharedSplits.groupBy { it.groupId ?: "${it.title}_${it.timestamp}" }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Universal Shared Splits & Group Ledger",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightGrey.copy(alpha = 0.8f)
                            )
                        }

                        splitGroups.forEach { (groupId, groupSplits) ->
                            item(key = groupId) {
                                val first = groupSplits.first()
                                val hasPending = groupSplits.any { it.status.equals("PENDING", ignoreCase = true) }
                                val allSettled = !hasPending

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(
                                        1.dp,
                                        if (allSettled) LightGrey.copy(alpha = 0.3f) else Color(0xFF6366F1).copy(alpha = 0.4f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // Group Header
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = first.title,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Total Bill: ${Utils.formatCurrency(first.totalAmount, currentCurrency)} • ${Utils.formatDateToHumanReadableForm(first.timestamp)}",
                                                    fontSize = 12.sp,
                                                    color = LightGrey
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(if (allSettled) LightGrey.copy(alpha = 0.2f) else Color(0xFF6366F1).copy(alpha = 0.15f))
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = if (allSettled) "All Settled" else "Pending (${groupSplits.count { it.status.equals("PENDING", ignoreCase = true) }})",
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (allSettled) LightGrey else Color(0xFF6366F1)
                                                    )
                                                }

                                                if (hasPending) {
                                                    Button(
                                                        onClick = {
                                                            first.groupId?.let { gid -> viewModel.settleAllInSplitGroup(gid) }
                                                        },
                                                        shape = RoundedCornerShape(8.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                                                    ) {
                                                        Text("Settle All", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Participants List
                                        groupSplits.forEachIndexed { pIdx, split ->
                                            val isCreator = split.creatorId == currentUserId || (split.creatorContact != null && (split.creatorContact == currentUserEmail || split.creatorContact == currentUserPhone))
                                            val (displayPerson, displayDirectionText, isPositiveForMe) = if (isCreator) {
                                                if (split.direction == "OWED_TO_CREATOR") Triple(split.counterpartyName, "owes you", true) else Triple(split.counterpartyName, "you owe", false)
                                            } else {
                                                if (split.direction == "OWED_TO_CREATOR") Triple(split.creatorName, "you owe", false) else Triple(split.creatorName, "owes you", true)
                                            }
                                            val isSettled = split.status.equals("SETTLED", ignoreCase = true)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 6.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = displayPerson,
                                                            fontSize = 13.5.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (!split.counterpartyId.isNullOrBlank()) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("✓", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                    Text(
                                                        text = displayDirectionText,
                                                        fontSize = 11.5.sp,
                                                        color = if (isSettled) LightGrey else if (isPositiveForMe) Green else Red
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(
                                                        text = Utils.formatCurrency(split.splitAmount, currentCurrency),
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isSettled) LightGrey else if (isPositiveForMe) Green else Red
                                                    )

                                                    if (!isSettled) {
                                                        Button(
                                                            onClick = { viewModel.settleSharedSplit(split.id) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1))
                                                        ) {
                                                            Text("Settle", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = { recordToDelete = DeleteRecordTarget.SharedSplit(split.id) },
                                                        modifier = Modifier.size(26.dp)
                                                    ) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_delete),
                                                            contentDescription = "Delete",
                                                            tint = Red.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (pIdx < groupSplits.size - 1) {
                                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            } else if (uiState.filterType == "Credit / Owe") {
                if (uiState.creditOweList.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_credit_owe),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = LightGrey
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No credit / owe records found.",
                                color = LightGrey,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Dedicated Credit & Owe Ledger",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightGrey.copy(alpha = 0.8f)
                            )
                        }

                        items(
                            items = uiState.creditOweList,
                            key = { it.id }
                        ) { co ->
                            val isCreator = co.userId == currentUserId
                            val (displayDirection, isPositive) = if (isCreator) {
                                if (co.type.equals("CREDIT", true)) Pair("owes you", true) else Pair("you owe", false)
                            } else {
                                if (co.type.equals("CREDIT", true)) Pair("you owe", false) else Pair("owes you", true)
                            }
                            val isSettled = co.status.equals("SETTLED", ignoreCase = true)
                            val isCloudSynced = !co.counterpartyId.isNullOrBlank() || co.userId.isNotBlank()

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { editingCreditOwe = co },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSettled) LightGrey.copy(alpha = 0.3f) else if (isPositive) Green.copy(alpha = 0.4f) else Red.copy(alpha = 0.4f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = co.personName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                if (!co.counterpartyId.isNullOrBlank()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("✓", fontSize = 11.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Text(
                                                text = displayDirection + if (!co.note.isNullOrBlank()) " • ${co.note}" else "",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSettled) LightGrey else if (isPositive) Green else Red
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = (if (isPositive) "+" else "-") + Utils.formatCurrency(co.amount, currentCurrency),
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSettled) LightGrey else if (isPositive) Green else Red
                                            )
                                            Text(
                                                text = Utils.formatDateToHumanReadableForm(co.createdAt),
                                                fontSize = 10.5.sp,
                                                color = LightGrey
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isSettled) LightGrey.copy(alpha = 0.2f) else Color(0xFFF59E0B).copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (isSettled) "Settled" else "Pending",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isSettled) LightGrey else Color(0xFFF59E0B)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(if (isCloudSynced) Green.copy(alpha = 0.15f) else Color(0xFFF59E0B).copy(alpha = 0.15f))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = if (isCloudSynced) "Synced" else "Local",
                                                    fontSize = 10.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCloudSynced) Green else Color(0xFFF59E0B)
                                                )
                                            }
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (!isSettled) {
                                                Button(
                                                    onClick = { viewModel.settleCreditOwe(co.id) },
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                                                ) {
                                                    Text("Settle Up", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                                }
                                            }

                                            IconButton(
                                                onClick = { recordToDelete = DeleteRecordTarget.CreditOwe(co.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_delete),
                                                    contentDescription = "Delete",
                                                    tint = Red.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            } else if (uiState.transactions.isEmpty()) {
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
                            fontSize = 13.sp,
                            color = LightGrey.copy(alpha = 0.8f),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }

                    items(
                        items = uiState.transactions,
                        key = { it.id ?: 0 }
                    ) { item ->
                        val currentItem by rememberUpdatedState(item)
                        val haptic = LocalHapticFeedback.current
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = { dismissValue ->
                                if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                    recordToDelete = DeleteRecordTarget.Transaction(currentItem)
                                    false
                                } else false
                            },
                            positionalThreshold = { totalDistance -> totalDistance * 0.55f }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                val targetProgress = kotlin.math.abs(dismissState.progress)
                                val bgAlpha = if (targetProgress >= 0.55f) 1f else (targetProgress * 1.5f).coerceIn(0.1f, 0.7f)
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Red.copy(alpha = bgAlpha))
                                        .padding(horizontal = 20.dp),
                                    contentAlignment = alignment
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = "Delete",
                                        tint = Color.White.copy(alpha = if (targetProgress >= 0.55f) 1f else 0.7f),
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
                                    val isIncomeLike = item.type.equals("Income", ignoreCase = true) || item.type.equals("Owed to You", ignoreCase = true)
                                    val isCreditOwe = item.type.equals("Owed to You", ignoreCase = true) || item.type.equals("You Owe", ignoreCase = true)
                                    val itemColor = when {
                                        isCreditOwe -> Color(0xFFF59E0B)
                                        isIncomeLike -> Green
                                        else -> Red
                                    }

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = {
                                                    selectedItemForReceipt = item
                                                },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    actionMenuItem = item
                                                }
                                            )
                                    ) {
                                        TransactionItem(
                                            title = item.title,
                                            amount = if (isIncomeLike) "+$formattedAmount" else "-$formattedAmount",
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

                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}
