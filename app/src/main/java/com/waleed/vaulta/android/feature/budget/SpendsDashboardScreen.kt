package com.waleed.vaulta.android.feature.budget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.InterFontFamily
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.widget.ExpenseTextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// Beautiful distinct category colors matching modern finance charts
private val CategoryPalette = listOf(
    Color(0xFFE57373), // Coral / Red (Groceries & Food)
    Color(0xFF81C784), // Soft Green (Transfers)
    Color(0xFF64B5F6), // Blue (Utility & Bills)
    Color(0xFFFFB74D), // Amber / Yellow (Shopping)
    Color(0xFFBA68C8), // Purple (Entertainment / Fuel)
    Color(0xFF4DB6AC), // Teal
    Color(0xFFFF8A65), // Deep Orange
    Color(0xFFA1887F), // Warm Brown
    Color(0xFF90A4AE)  // Blue Grey
)

data class CategorySpendItem(
    val categoryName: String,
    val amount: Double,
    val percentage: Float,
    val color: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SpendsDashboardScreen(
    navController: NavController,
    viewModel: SpendsDashboardViewModel = hiltViewModel()
) {
    val expenses by viewModel.expenses.collectAsState(initial = emptyList())
    val monthlyBudget by viewModel.monthlyBudget.collectAsState()

    var showSetBudgetDialog by remember { mutableStateOf(false) }

    // Calculate current month's total spending
    val currentMonthSpends = remember(expenses) {
        viewModel.calculateCurrentMonthSpends(expenses)
    }

    // Group expenses into categories
    val categoryItems = remember(expenses, currentMonthSpends) {
        val expenseList = expenses.filter { it.type != "Income" }
        val grouped = expenseList.groupBy { it.title.trim().ifEmpty { "Other Expenses" } }
        val items = mutableListOf<CategorySpendItem>()

        var colorIndex = 0
        grouped.entries
            .sortedByDescending { it.value.sumOf { e -> e.amount } }
            .forEach { entry ->
                val categorySum = entry.value.sumOf { it.amount }
                val percent = if (currentMonthSpends > 0) ((categorySum / currentMonthSpends) * 100).toFloat() else 0f
                val color = CategoryPalette[colorIndex % CategoryPalette.size]
                items.add(
                    CategorySpendItem(
                        categoryName = entry.key,
                        amount = categorySum,
                        percentage = percent,
                        color = color
                    )
                )
                colorIndex++
            }
        items
    }

    val currentMonthName = remember {
        val cal = Calendar.getInstance()
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
    }

    // Set Budget Dialog
    if (showSetBudgetDialog) {
        SetBudgetDialog(
            currentBudget = monthlyBudget,
            onSave = { newBudget ->
                viewModel.setMonthlyBudget(newBudget, expenses)
                showSetBudgetDialog = false
            },
            onDismiss = {
                showSetBudgetDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_back),
                    contentDescription = "Back",
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable { navController.popBackStack() },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
                ExpenseTextView(
                    text = "Spends Dashboard",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.CenterEnd)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { showSetBudgetDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_edit),
                        contentDescription = "Set Budget",
                        modifier = Modifier.size(18.dp),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Header: Spends this month + Set Budget pill button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    ExpenseTextView(
                        text = "Spends this month",
                        fontSize = 13.sp,
                        color = LightGrey,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ExpenseTextView(
                        text = Utils.formatCurrency(currentMonthSpends),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Orange/Coral Pill Button (Matching BHIM UPI design)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFFE67E22))
                        .clickable { showSetBudgetDialog = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (monthlyBudget > 0) "Edit Budget" else "Set Budget",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFontFamily
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monthly Budget Card (if budget is set)
            if (monthlyBudget > 0.0) {
                val percentUsed = ((currentMonthSpends / monthlyBudget) * 100).toFloat()
                val isExceeded = currentMonthSpends >= monthlyBudget
                val isApproaching = percentUsed >= 80f && !isExceeded
                val remaining = (monthlyBudget - currentMonthSpends).coerceAtLeast(0.0)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isExceeded -> Color(0xFFFFEBEE)
                            isApproaching -> Color(0xFFFFF3E0)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = when {
                                        isExceeded -> "🚨 Budget Exceeded"
                                        isApproaching -> "⚠️ Approaching Limit"
                                        else -> "🎯 Monthly Budget"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = when {
                                        isExceeded -> Red
                                        isApproaching -> Color(0xFFE65100)
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                            }
                            Text(
                                text = "${percentUsed.toInt()}% used",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = when {
                                    isExceeded -> Red
                                    isApproaching -> Color(0xFFE65100)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Animated Progress Bar
                        val animatedProgress by animateFloatAsState(
                            targetValue = (currentMonthSpends / monthlyBudget).toFloat().coerceIn(0f, 1f),
                            label = "BudgetProgress"
                        )

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when {
                                isExceeded -> Red
                                isApproaching -> Color(0xFFE67E22)
                                else -> Green
                            },
                            trackColor = Color.Black.copy(alpha = 0.1f)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Budget: ${Utils.formatCurrency(monthlyBudget)}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = if (isExceeded) {
                                    "Over by ${Utils.formatCurrency(currentMonthSpends - monthlyBudget)}"
                                } else {
                                    "Left: ${Utils.formatCurrency(remaining)}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isExceeded) Red else Green
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Category Spends Card (Exact BHIM UPI style)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header Row: Category Spends + Month Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ExpenseTextView(
                            text = "Category Spends",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Month pill
                        Box(
                            modifier = Modifier
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "$currentMonthName ▾",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "Top Categories",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Segmented Multi-Colored Horizontal Progress Bar (BHIM UPI visual!)
                    if (categoryItems.isNotEmpty()) {
                        SegmentedCategoryProgressBar(
                            categories = categoryItems,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No category spends recorded yet",
                                fontSize = 13.sp,
                                color = LightGrey
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Categories List with bullet dots, category names and amounts
                    if (categoryItems.isNotEmpty()) {
                        categoryItems.forEachIndexed { index, item ->
                            CategorySpendRow(item = item)
                            if (index < categoryItems.size - 1) {
                                Spacer(modifier = Modifier.height(14.dp))
                            }
                        }
                    } else {
                        Text(
                            text = "Add expenses with names like Grocery, Food, Utilities, Shopping to see them categorized automatically here.",
                            fontSize = 13.sp,
                            color = LightGrey,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom Action Button
                    OutlinedButton(
                        onClick = { showSetBudgetDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Text(
                            text = if (monthlyBudget > 0) "Adjust Monthly Budget" else "Set Monthly Budget",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * Renders a segmented horizontal bar representing relative category proportions.
 */
@Composable
fun SegmentedCategoryProgressBar(
    categories: List<CategorySpendItem>,
    modifier: Modifier = Modifier
) {
    val totalAmount = categories.sumOf { it.amount }
    if (totalAmount <= 0.0) return

    Row(modifier = modifier) {
        categories.forEachIndexed { index, item ->
            val weight = (item.amount / totalAmount).toFloat().coerceAtLeast(0.01f)
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
                    .background(item.color)
                    .then(
                        if (index < categories.size - 1) {
                            Modifier.padding(end = 2.dp)
                        } else Modifier
                    )
            )
        }
    }
}

/**
 * Individual row showing colored circular dot, category name, and amount.
 */
@Composable
fun CategorySpendRow(item: CategorySpendItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Circular category color dot
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(item.color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = item.categoryName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.percentage > 0) {
                    Text(
                        text = "${String.format(Locale.getDefault(), "%.1f", item.percentage)}% of total spends",
                        fontSize = 13.sp,
                        color = LightGrey
                    )
                }
            }
        }

        Text(
            text = Utils.formatCurrency(item.amount),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * Set/Edit Monthly Budget Dialog with quick preset chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetBudgetDialog(
    currentBudget: Double,
    onSave: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    var budgetInput by remember {
        mutableStateOf(if (currentBudget > 0) currentBudget.toInt().toString() else "")
    }

    val presetAmounts = listOf(5000, 10000, 20000, 30000, 50000, 100000)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Monthly Budget",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column {
                Text(
                    text = "Specify your monthly spending limit. Vaulta will notify you when you approach 80% and when you reach your limit.",
                    fontSize = 13.sp,
                    color = LightGrey
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = budgetInput,
                    onValueChange = { newValue ->
                        budgetInput = newValue.filter { it.isDigit() }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Monthly Budget (${CurrencyManager.getSelectedCurrencySymbol()})") },
                    prefix = { Text("${CurrencyManager.getSelectedCurrencySymbol()} ", fontWeight = FontWeight.Bold) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Quick Presets:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = LightGrey
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    presetAmounts.forEach { amount ->
                        SuggestionChip(
                            onClick = { budgetInput = amount.toString() },
                            label = { Text("${CurrencyManager.getSelectedCurrencySymbol()}${amount / 1000}k", fontSize = 12.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = budgetInput.toDoubleOrNull() ?: 0.0
                    onSave(amount)
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save Budget", color = Color.White)
            }
        },
        dismissButton = {
            Row {
                if (currentBudget > 0) {
                    TextButton(onClick = { onSave(0.0) }) {
                        Text("Remove", color = Red)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = LightGrey)
                }
            }
        }
    )
}
