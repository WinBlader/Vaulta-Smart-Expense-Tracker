package com.waleed.vaulta.android.feature.transactionlist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.widget.ExpenseTextView
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    item: ExpenseEntity,
    onSave: (ExpenseEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(item.title) }
    var amount by remember { mutableStateOf(String.format(Locale.US, "%.2f", item.amount)) }
    var type by remember { mutableStateOf(if (item.type.equals("Income", ignoreCase = true)) "Income" else "Expense") }
    var dateMillis by remember {
        mutableLongStateOf(
            Utils.getMillisFromDate(item.date).let { if (it > 0L) it else System.currentTimeMillis() }
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val currentCurrency by CurrencyManager.selectedCurrency.collectAsState()

    val incomePresets = listOf("Salary", "Freelance", "Investments", "Bonus", "Rental Income", "Gifts", "Other Income")
    val expensePresets = listOf("Grocery", "Food", "Rent", "Shopping", "Transport", "Utilities", "Entertainment", "Healthcare", "Other Expenses")
    val activePresets = if (type == "Income") incomePresets else expensePresets

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { dateMillis = it }
                        showDatePicker = false
                    }
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold, color = Zinc)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = LightGrey)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 700.dp)
                .shadow(24.dp, shape = RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        ExpenseTextView(
                            text = "Edit Transaction",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        ExpenseTextView(
                            text = "Update transaction details below",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                    }
                    TextButton(onClick = onDismiss) {
                        ExpenseTextView(text = "✕", fontSize = 18.sp, color = LightGrey, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Type Toggle (Income vs Expense)
                ExpenseTextView(
                    text = "TRANSACTION TYPE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Expense Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (type == "Expense") Red.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { type = "Expense" }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Expense",
                            fontSize = 14.sp,
                            fontWeight = if (type == "Expense") FontWeight.Bold else FontWeight.Normal,
                            color = if (type == "Expense") Red else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Income Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (type == "Income") Green.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { type = "Income" }
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Income",
                            fontSize = 14.sp,
                            fontWeight = if (type == "Income") FontWeight.Bold else FontWeight.Normal,
                            color = if (type == "Income") Green else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Quick Category Chips
                ExpenseTextView(
                    text = "QUICK CATEGORIES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(activePresets) { preset ->
                        FilterChip(
                            selected = title.equals(preset, ignoreCase = true),
                            onClick = { title = preset },
                            label = { Text(preset, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Zinc.copy(alpha = 0.2f),
                                selectedLabelColor = Zinc
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title / Merchant Field
                ExpenseTextView(
                    text = "TITLE / MERCHANT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Zinc,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    placeholder = { Text("e.g. Grocery, Navi Limited, Salary", color = LightGrey, fontSize = 13.sp) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Amount Field
                ExpenseTextView(
                    text = "AMOUNT (${currentCurrency.symbol})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = {
                        amount = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(10.dp),
                    prefix = { Text("${currentCurrency.symbol} ", fontWeight = FontWeight.Bold, color = Zinc) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Zinc,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    placeholder = { Text("0.00", color = LightGrey, fontSize = 13.sp) }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Date Picker Field
                ExpenseTextView(
                    text = "TRANSACTION DATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = LightGrey,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showDatePicker = true }
                ) {
                    OutlinedTextField(
                        value = Utils.formatDateToHumanReadableForm(dateMillis),
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }

                // Error Message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Button(
                    onClick = {
                        val parsedAmount = amount.trim().toDoubleOrNull()
                        if (parsedAmount == null || parsedAmount <= 0.0) {
                            errorMessage = "Please enter a valid amount greater than zero."
                            return@Button
                        }
                        val cleanTitle = title.trim()
                        if (cleanTitle.isBlank()) {
                            errorMessage = "Please enter a transaction title."
                            return@Button
                        }

                        val updatedEntity = item.copy(
                            title = cleanTitle,
                            amount = parsedAmount,
                            type = type,
                            date = Utils.formatDateToHumanReadableForm(dateMillis)
                        )
                        onSave(updatedEntity)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Zinc)
                ) {
                    ExpenseTextView(text = "Save Changes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ExpenseTextView(text = "Cancel", color = LightGrey, fontSize = 13.sp)
                }
            }
        }
    }
}
