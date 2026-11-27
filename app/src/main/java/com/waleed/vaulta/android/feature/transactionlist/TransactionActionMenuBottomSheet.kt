package com.waleed.vaulta.android.feature.transactionlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.utils.Utils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionActionMenuBottomSheet(
    item: ExpenseEntity,
    sheetState: SheetState = rememberModalBottomSheetState(),
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentCurrency = CurrencyManager.getSelectedCurrency()
    val isIncomeLike = item.type.equals("Income", ignoreCase = true) || item.type.equals("Owed to You", ignoreCase = true)
    val isCreditOwe = item.type.equals("Owed to You", ignoreCase = true) || item.type.equals("You Owe", ignoreCase = true)
    val formattedAmount = Utils.formatCurrency(item.amount, currentCurrency)
    val itemColor = when {
        isCreditOwe -> Color(0xFFF59E0B)
        isIncomeLike -> Green
        else -> Red
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            // Header: Icon + Title + Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = Utils.getItemIcon(item)),
                        contentDescription = null,
                        tint = itemColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${item.type} • ${Utils.formatStringDateToMonthDayYear(item.date)}",
                        fontSize = 12.5.sp,
                        color = LightGrey
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (isIncomeLike) "+$formattedAmount" else "-$formattedAmount",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = itemColor
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(16.dp))

            // Action: Edit / View Details
            Button(
                onClick = {
                    onDismiss()
                    onEdit()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Edit Transaction",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Delete Transaction (Triggers same undo flow)
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onDelete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Red),
                border = androidx.compose.foundation.BorderStroke(1.dp, Red.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Red
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Delete Transaction",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Red
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Cancel
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    color = LightGrey
                )
            }
        }
    }
}
