package com.waleed.vaulta.android.feature.transactionlist

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.ReceiptExportManager
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun TransactionReceiptDialog(
    item: ExpenseEntity,
    onEdit: ((ExpenseEntity) -> Unit)? = null,
    onDelete: ((ExpenseEntity) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentItem by remember(item) { mutableStateOf(item) }
    val isIncome = currentItem.type.equals("Income", ignoreCase = true)
    val amountSign = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) Green else Red

    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Edit Transaction Dialog
    if (showEditDialog) {
        EditTransactionDialog(
            item = currentItem,
            onSave = { updated ->
                currentItem = updated
                showEditDialog = false
                onEdit?.invoke(updated)
            },
            onDismiss = { showEditDialog = false }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        colorFilter = ColorFilter.tint(Red)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete Transaction?",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${currentItem.title}\" (${Utils.formatCurrency(currentItem.amount)})? You can undo this action immediately after.",
                    fontSize = 14.sp,
                    color = LightGrey
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete?.invoke(currentItem)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel", color = LightGrey)
                }
            }
        )
    }

    // Main Receipt Dialog
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .heightIn(max = 720.dp)
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
                // Header Row with Brand & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VAULTA RECEIPT",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Zinc,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Official Transaction Record",
                            fontSize = 11.sp,
                            color = LightGrey
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✕", fontSize = 14.sp, color = LightGrey, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Title
                ExpenseTextView(
                    text = currentItem.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Receipt Amount & Details Container
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large Amount
                    Text(
                        text = "$amountSign${Utils.formatCurrency(currentItem.amount)}",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = amountColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Type Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isIncome) Green.copy(alpha = 0.18f) else Red.copy(alpha = 0.18f))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isIncome) "INCOME / CREDIT" else "EXPENSE / DEBIT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = amountColor
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(10.dp))

                    // Detail Rows
                    DetailRow(label = "Date", value = Utils.formatStringDateToMonthDayYear(currentItem.date))
                    DetailRow(label = "Type", value = currentItem.type)
                    DetailRow(label = "Reference ID", value = "VLT-${currentItem.id ?: (System.currentTimeMillis() % 100000)}")
                    DetailRow(label = "Payment Status", value = "Completed • Verified")
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Action Buttons (Stack with strict spacing to eliminate overlap)
                // Row 1: Download PDF & Share PDF
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            ReceiptExportManager.downloadTransactionReceiptPdf(context, currentItem)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Zinc)
                    ) {
                        Text(
                            text = "Download PDF",
                            fontSize = 12.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            ReceiptExportManager.shareTransactionReceiptAsPdf(context, currentItem)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Zinc.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = "Share PDF",
                            fontSize = 12.sp,
                            color = Zinc,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 2: Share PNG & Edit Transaction
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            ReceiptExportManager.shareTransactionReceiptAsImage(context, currentItem)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = "Share PNG",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    if (onEdit != null) {
                        OutlinedButton(
                            onClick = { showEditDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Zinc.copy(alpha = 0.7f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = "Edit",
                                    modifier = Modifier.size(15.dp),
                                    colorFilter = ColorFilter.tint(Zinc)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Edit",
                                    fontSize = 12.sp,
                                    color = Zinc,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                // Row 3: Delete Transaction Button (if supported)
                if (onDelete != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                        border = BorderStroke(1.dp, Red.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = "Delete",
                                modifier = Modifier.size(16.dp),
                                colorFilter = ColorFilter.tint(Red)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Delete Transaction",
                                fontSize = 12.5.sp,
                                color = Red,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }

                // Row 4: Dismiss TextButton with explicit spacing & padding
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Text("Dismiss", color = LightGrey, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 12.5.sp, color = LightGrey)
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
