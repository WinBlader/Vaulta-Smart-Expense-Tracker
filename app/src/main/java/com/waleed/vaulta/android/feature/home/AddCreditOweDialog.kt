package com.waleed.vaulta.android.feature.home

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.widget.DatePicker
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.CreditOweEntity
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.widget.ContactPickerBottomSheet
import java.util.Calendar

@Composable
fun AddCreditOweDialog(
    initialEntity: CreditOweEntity? = null,
    onDismiss: () -> Unit,
    onLookupContact: (suspend (String) -> com.waleed.vaulta.android.data.model.ContactLookupResult)? = null,
    onSearchUsers: (suspend (String) -> List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult>)? = null,
    onSettle: (() -> Unit)? = null,
    onSave: (personName: String, amount: Double, type: String, note: String?, contact: String?, counterpartyId: String?) -> Unit
) {
    val context = LocalContext.current
    val currentCurrency = CurrencyManager.selectedCurrency.collectAsState().value

    // Direction: true = Someone Owes You (CREDIT), false = You Owe Someone (OWE)
    var isSomeoneOwesYou by remember {
        mutableStateOf(initialEntity?.type?.equals("CREDIT", true) ?: true)
    }
    var personName by remember { mutableStateOf(initialEntity?.personName ?: "") }
    var personContact by remember { mutableStateOf(initialEntity?.counterpartyContact ?: "") }
    var amount by remember {
        mutableStateOf(initialEntity?.amount?.let { if (it > 0) it.toString() else "" } ?: "")
    }
    var date by remember { mutableStateOf(initialEntity?.createdAt ?: System.currentTimeMillis()) }
    var note by remember { mutableStateOf(initialEntity?.note ?: "") }
    var counterpartyId by remember { mutableStateOf<String?>(initialEntity?.counterpartyId) }
    var lookupResult by remember { mutableStateOf<com.waleed.vaulta.android.data.model.ContactLookupResult?>(null) }
    var isLookingUp by remember { mutableStateOf(false) }
    var searchSuggestions by remember { mutableStateOf<List<com.waleed.vaulta.android.data.model.VaultaUserSearchResult>>(emptyList()) }
    var isSearchingUsers by remember { mutableStateOf(false) }

    var showContactPicker by remember { mutableStateOf(false) }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showContactPicker = true
        } else {
            Toast.makeText(context, "Contacts permission allows picking friends easily. You can still type manually.", Toast.LENGTH_SHORT).show()
        }
    }

    if (showContactPicker) {
        ContactPickerBottomSheet(
            onDismiss = { showContactPicker = false },
            onContactSelected = { cName, cContact ->
                personName = cName
                personContact = cContact
            }
        )
    }

    LaunchedEffect(personContact) {
        val trimmed = personContact.trim()
        if (trimmed.length < 3 || onLookupContact == null) {
            counterpartyId = null
            lookupResult = null
            isLookingUp = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(400)
        isLookingUp = true
        val res = onLookupContact(trimmed)
        isLookingUp = false
        lookupResult = res
        if (res.found) {
            counterpartyId = res.userId
            if (personName.isBlank() && !res.displayName.isNullOrBlank()) {
                personName = res.displayName
            }
        } else {
            counterpartyId = null
        }
    }

    LaunchedEffect(personName, personContact) {
        val query = personName.trim().ifEmpty { personContact.trim() }
        if (query.length < 2 || onSearchUsers == null) {
            searchSuggestions = emptyList()
            isSearchingUsers = false
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(300)
        isSearchingUsers = true
        val results = try {
            onSearchUsers(query)
        } catch (_: Exception) {
            emptyList()
        }
        isSearchingUsers = false
        searchSuggestions = results.filter { it.userId != counterpartyId }
    }

    val calendar = Calendar.getInstance().apply { timeInMillis = date }
    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
            val newCal = Calendar.getInstance()
            newCal.set(year, month, dayOfMonth)
            date = newCal.timeInMillis
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(Color(0xFFF59E0B), shape = RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_credit_owe),
                            contentDescription = "Credit/Owe",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (initialEntity != null) "Edit Credit & Owe" else "Credit & Owe",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Direction Toggle
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
                            .background(if (isSomeoneOwesYou) Color(0xFFA855F7) else Color.Transparent)
                            .clickable { isSomeoneOwesYou = true }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Owed to You (+)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSomeoneOwesYou) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isSomeoneOwesYou) Color(0xFFEC4899) else Color.Transparent)
                            .clickable { isSomeoneOwesYou = false }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "You Owe (-)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (!isSomeoneOwesYou) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Person Name Field
                OutlinedTextField(
                    value = personName,
                    onValueChange = { personName = it },
                    label = { Text("Person Name") },
                    placeholder = { Text("e.g. John Doe, Alex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Person Contact (Optional for sync) + Pick from Contacts button
                OutlinedTextField(
                    value = personContact,
                    onValueChange = { personContact = it },
                    label = { Text("Email or Phone (Optional for Sync)") },
                    placeholder = { Text("john@example.com or +1234567890") },
                    trailingIcon = {
                        IconButton(onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                                showContactPicker = true
                            } else {
                                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                            }
                        }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_profile),
                                contentDescription = "Pick Contact",
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (isLookingUp) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Checking Vaulta user...", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else if (lookupResult != null && personContact.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (lookupResult!!.found) {
                        val nameToShow = lookupResult!!.displayName ?: "Vaulta user"
                        Text(
                            text = "Vaulta user found: $nameToShow ✓",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF10B981)
                        )
                    } else {
                        Text(
                            text = "Not registered yet — will auto-sync when they join or add this contact",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                // Autocomplete dropdown of matching Vaulta users
                if (searchSuggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text(
                                text = "Matching Vaulta Users:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF59E0B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            searchSuggestions.take(4).forEach { userResult ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            personName = userResult.displayName
                                            counterpartyId = userResult.userId
                                            if (!userResult.maskedContact.isNullOrBlank()) {
                                                personContact = userResult.maskedContact
                                            }
                                            searchSuggestions = emptyList()
                                        }
                                        .padding(horizontal = 6.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFFF59E0B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = userResult.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (!userResult.maskedContact.isNullOrBlank()) {
                                            Text(
                                                text = userResult.maskedContact,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Select ✓",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Amount Field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) amount = it },
                    label = { Text("Amount (${currentCurrency.symbol})") },
                    placeholder = { Text("0.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Date Picker Row
                OutlinedCard(
                    onClick = { datePickerDialog.show() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Date: ${Utils.formatDateToHumanReadableForm(date)}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            painter = painterResource(R.drawable.ic_stats),
                            contentDescription = "Pick Date",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Optional Note Field
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    placeholder = { Text("Add a note (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (initialEntity != null && initialEntity.status.equals("PENDING", ignoreCase = true) && onSettle != null) {
                        Button(
                            onClick = {
                                onSettle()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Settle Up", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                            if (parsedAmount > 0 && personName.isNotBlank()) {
                                val typeStr = if (isSomeoneOwesYou) "CREDIT" else "OWE"
                                onSave(
                                    personName.trim(),
                                    parsedAmount,
                                    typeStr,
                                    note.trim().ifBlank { null },
                                    personContact.trim().ifBlank { null },
                                    counterpartyId
                                )
                            }
                        },
                        enabled = personName.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF59E0B)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Save", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

