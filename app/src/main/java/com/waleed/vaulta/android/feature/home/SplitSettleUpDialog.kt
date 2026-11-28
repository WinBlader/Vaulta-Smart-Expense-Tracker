package com.waleed.vaulta.android.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.SharedSplitEntity
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.utils.Utils

/**
 * Two-Option Settle Up Dialog for Splits:
 * Option 1: "Settle All" -> Marks all pending participant shares in that split as SETTLED.
 * Option 2: "Custom" -> Expands the participant checklist + custom settlement amount input so the user
 * can select specific people who paid their share OR enter a custom partial amount paid.
 */
@Composable
fun SplitSettleUpDialog(
    splitTitle: String,
    totalBill: Double,
    pendingSplits: List<SharedSplitEntity>,
    onDismiss: () -> Unit,
    onSettleAll: () -> Unit,
    onSettleCustom: (selectedSplits: List<SharedSplitEntity>, customAmounts: Map<String, Double>) -> Unit
) {
    var mode by remember { mutableStateOf("ALL") } // "ALL" or "CUSTOM"
    val currentCurrency = CurrencyManager.selectedCurrency.collectAsState().value

    // For Custom mode:
    var selectedIds by remember { mutableStateOf(pendingSplits.map { it.id }.toSet()) }
    var customAmounts by remember {
        mutableStateOf(pendingSplits.associate { it.id to it.splitAmount.toString() })
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Settle Split Bill",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$splitTitle • Total ${Utils.formatCurrency(totalBill, currentCurrency)}",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Selector: Settle All vs Custom
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
                            .background(if (mode == "ALL") Color(0xFF6366F1) else Color.Transparent)
                            .clickable { mode = "ALL" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Settle All",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "ALL") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (mode == "CUSTOM") Color(0xFF6366F1) else Color.Transparent)
                            .clickable { mode = "CUSTOM" }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Custom Settle",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (mode == "CUSTOM") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (mode == "ALL") {
                    val totalPending = pendingSplits.sumOf { it.splitAmount }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Mark all ${pendingSplits.size} pending participant shares as fully settled.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total amount to credit back:",
                                    fontSize = 12.sp,
                                    color = LightGrey
                                )
                                Text(
                                    text = Utils.formatCurrency(totalPending, currentCurrency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Green
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        text = "Select participants and adjust paid amounts:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LightGrey
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        itemsIndexed(pendingSplits) { _, split ->
                            val isChecked = selectedIds.contains(split.id)
                            val currentAmt = customAmounts[split.id] ?: split.splitAmount.toString()

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedIds = if (checked) {
                                            selectedIds + split.id
                                        } else {
                                            selectedIds - split.id
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF6366F1))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = split.counterpartyName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Original: ${Utils.formatCurrency(split.splitAmount, currentCurrency)}",
                                        fontSize = 11.sp,
                                        color = LightGrey
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = currentAmt,
                                    onValueChange = { newText ->
                                        if (newText.all { c -> c.isDigit() || c == '.' }) {
                                            customAmounts = customAmounts + (split.id to newText)
                                        }
                                    },
                                    enabled = isChecked,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    singleLine = true,
                                    modifier = Modifier.width(90.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = LightGrey)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (mode == "ALL") {
                                onSettleAll()
                            } else {
                                val selectedList = pendingSplits.filter { selectedIds.contains(it.id) }
                                val parsedAmounts = selectedList.associate {
                                    it.id to (customAmounts[it.id]?.toDoubleOrNull() ?: it.splitAmount)
                                }
                                onSettleCustom(selectedList, parsedAmounts)
                            }
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Confirm Settle", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
