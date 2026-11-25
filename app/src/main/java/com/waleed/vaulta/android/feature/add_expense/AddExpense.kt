@file:OptIn(ExperimentalMaterial3Api::class)

package com.waleed.vaulta.android.feature.add_expense

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.base.AddExpenseNavigationEvent
import com.waleed.vaulta.android.base.NavigationEvent
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.data.model.ExpenseEntity
import com.waleed.vaulta.android.ui.theme.InterFontFamily
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Typography
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.widget.ExpenseTextView

@Composable
fun AddExpense(
    navController: NavController,
    isIncome: Boolean,
    viewModel: AddExpenseViewModel = hiltViewModel()
) {
    val menuExpanded = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                NavigationEvent.NavigateBack -> navController.popBackStack()
                AddExpenseNavigationEvent.MenuOpenedClicked -> {
                    menuExpanded.value = true
                }
                else->{}
            }
        }
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.material3.MaterialTheme.colorScheme.background
    ) {
        ConstraintLayout(modifier = Modifier.fillMaxSize()) {
            val (nameRow, card, topBar) = createRefs()
            Image(painter = painterResource(id = R.drawable.ic_topbar),
                contentDescription = null,
                modifier = Modifier.constrainAs(topBar) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                })
            Box(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 64.dp, start = 16.dp, end = 16.dp)
                .constrainAs(nameRow) {
                    top.linkTo(parent.top)
                    start.linkTo(parent.start)
                    end.linkTo(parent.end)
                }) {
                Image(painter = painterResource(id = R.drawable.ic_back), contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clickable {
                            viewModel.onEvent(AddExpenseUiEvent.OnBackPressed)
                        })
                ExpenseTextView(
                    text = "Add ${if (isIncome) "Income" else "Expense"}",
                    style = Typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.Center)
                )

            }
            DataForm(modifier = Modifier.constrainAs(card) {
                top.linkTo(nameRow.bottom)
                start.linkTo(parent.start)
                end.linkTo(parent.end)
            }, onAddExpenseClick = {
                viewModel.onEvent(AddExpenseUiEvent.OnAddExpenseClicked(it))
            }, isIncome)
        }
    }
}

@Composable
fun DataForm(
    modifier: Modifier,
    onAddExpenseClick: (model: ExpenseEntity) -> Unit,
    isIncome: Boolean
) {

    val defaultCategory = if (isIncome) "Salary" else "Grocery"
    var isCustomName by remember { mutableStateOf(false) }
    var customNameText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(defaultCategory) }
    val name = remember {
        mutableStateOf(defaultCategory)
    }
    val amount = remember {
        mutableStateOf("")
    }
    val date = remember {
        mutableLongStateOf(System.currentTimeMillis())
    }
    val dateDialogVisibility = remember {
        mutableStateOf(false)
    }
    val type = remember {
        mutableStateOf(if (isIncome) "Income" else "Expense")
    }
    val note = remember {
        mutableStateOf("")
    }

    val presetList = if (isIncome) listOf(
        "Salary",
        "Freelance",
        "Investments",
        "Bonus",
        "Rental Income",
        "Gifts & Donations",
        "Other Income",
        "+ Write Custom Title..."
    ) else listOf(
        "Grocery",
        "Food",
        "Rent",
        "Shopping",
        "Transport",
        "Utilities",
        "Dining Out",
        "Entertainment",
        "Healthcare",
        "Insurance",
        "Subscriptions",
        "Education",
        "Debt Payments",
        "Tax",
        "Gifts & Donations",
        "Travel",
        "Ott",
        "Other Expenses",
        "+ Write Custom Title..."
    )

    Column(
        modifier = modifier
            .padding(16.dp)
            .fillMaxWidth()
            .shadow(16.dp)
            .clip(
                RoundedCornerShape(16.dp)
            )
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surface)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpenseTextView(
                text = "NAME / TITLE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = LightGrey
            )

            // Switch button between Preset and Custom
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isCustomName) Zinc.copy(alpha = 0.12f) else Color(0xFFF1F5F9))
                    .clickable {
                        isCustomName = !isCustomName
                        if (isCustomName) {
                            if (customNameText.isNotBlank()) {
                                name.value = customNameText.trim()
                            }
                        } else {
                            name.value = selectedCategory
                        }
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Image(
                    painter = painterResource(id = if (isCustomName) R.drawable.ic_stats else R.drawable.ic_edit),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    colorFilter = ColorFilter.tint(if (isCustomName) Zinc else Color.DarkGray)
                )
                Spacer(modifier = Modifier.width(4.dp))
                ExpenseTextView(
                    text = if (isCustomName) "Choose Preset" else "✏️ Write Custom Title",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCustomName) Zinc else Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.size(10.dp))

        if (isCustomName) {
            OutlinedTextField(
                value = customNameText,
                onValueChange = {
                    customNameText = it
                    name.value = it
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    ExpenseTextView(
                        text = if (isIncome) "e.g. Consulting, Client Project, Crypto..." else "e.g. Gym membership, Dinner at Taj, Uber...",
                        color = LightGrey
                    )
                },
                textStyle = TextStyle(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontFamily = InterFontFamily
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
                ),
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            ExpenseTextView(
                text = "← Switch back to predefined categories",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clickable {
                        isCustomName = false
                        name.value = selectedCategory
                    }
                    .padding(vertical = 4.dp)
            )
        } else {
            ExpenseDropDown(
                listOfItems = presetList,
                selectedItem = selectedCategory,
                onItemSelected = { selected ->
                    if (selected == "+ Write Custom Title...") {
                        isCustomName = true
                    } else {
                        selectedCategory = selected
                        name.value = selected
                    }
                }
            )
        }
        Spacer(modifier = Modifier.size(24.dp))
        TitleComponent("amount")
        val currentCurrency = CurrencyManager.selectedCurrency.collectAsState().value
        val symbolPrefix = if (currentCurrency.symbol.any { ch -> ch.isLetter() }) "${currentCurrency.symbol} " else currentCurrency.symbol

        OutlinedTextField(
            value = amount.value,
            onValueChange = { newValue ->
                amount.value = newValue.filter { it.isDigit() || it == '.' }
            },
            textStyle = TextStyle(color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface),
            visualTransformation = { text ->
                val out = symbolPrefix + text.text
                val currencyOffsetTranslator = object : OffsetMapping {
                    override fun originalToTransformed(offset: Int): Int {
                        return offset + symbolPrefix.length
                    }

                    override fun transformedToOriginal(offset: Int): Int {
                        return if (offset > symbolPrefix.length) offset - symbolPrefix.length else 0
                    }
                }

                TransformedText(AnnotatedString(out), currencyOffsetTranslator)
            },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { ExpenseTextView(text = "Enter amount", color = LightGrey) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                disabledPlaceholderColor = LightGrey,
                focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(modifier = Modifier.size(24.dp))
       TitleComponent("date")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
        ) {
            OutlinedTextField(
                value = Utils.formatDateToHumanReadableForm(date.longValue),
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                readOnly = true,
                trailingIcon = {
                    Image(
                        painter = painterResource(id = R.drawable.ic_stats),
                        contentDescription = "Select Date",
                        modifier = Modifier.size(20.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(androidx.compose.material3.MaterialTheme.colorScheme.onSurface)
                    )
                },
                textStyle = TextStyle(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    fontFamily = InterFontFamily
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                    disabledTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                    disabledPlaceholderColor = LightGrey,
                    disabledTrailingIconColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                ),
                placeholder = {
                    ExpenseTextView(text = "Select date", color = LightGrey)
                }
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable {
                        dateDialogVisibility.value = true
                    }
            )
        }

        if (dateDialogVisibility.value) {
            ExpenseDatePickerDialog(
                initialDate = date.longValue,
                onDateSelected = { selectedDate ->
                    date.longValue = selectedDate
                    dateDialogVisibility.value = false
                },
                onDismiss = {
                    dateDialogVisibility.value = false
                }
            )
        }

        Spacer(modifier = Modifier.size(16.dp))

        // Optional Note Field
        ExpenseTextView(
            text = "NOTE (OPTIONAL)",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = LightGrey
        )
        Spacer(modifier = Modifier.size(6.dp))
        OutlinedTextField(
            value = note.value,
            onValueChange = { note.value = it },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            placeholder = {
                ExpenseTextView(text = "Add a note (optional)", color = LightGrey)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
            ),
            shape = RoundedCornerShape(8.dp)
        )

        Spacer(modifier = Modifier.size(24.dp))

        val buttonColor = if (isIncome) Color(0xFF2E7D32) else Color(0xFFD32F2F)

        Button(
            onClick = {
                val finalDate = if (date.longValue <= 0L) System.currentTimeMillis() else date.longValue
                val finalName = if (isCustomName) {
                    if (customNameText.isNotBlank()) customNameText.trim() else (if (isIncome) "Income" else "Expense")
                } else {
                    if (name.value.isNotBlank() && name.value != "+ Write Custom Title...") name.value else selectedCategory
                }
                val model = ExpenseEntity(
                    id = null,
                    title = finalName,
                    amount = amount.value.toDoubleOrNull() ?: 0.0,
                    date = Utils.formatDateToHumanReadableForm(finalDate),
                    type = type.value,
                    note = note.value.trim().ifBlank { null }
                )
                onAddExpenseClick(model)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = buttonColor)
        ) {
            ExpenseTextView(
                text = "Add ${if (isIncome) "Income" else "Expense"}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDatePickerDialog(
    initialDate: Long = System.currentTimeMillis(),
    onDateSelected: (date: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = if (initialDate > 0L) initialDate else System.currentTimeMillis()
    )
    val selectedDate = datePickerState.selectedDateMillis ?: System.currentTimeMillis()
    DatePickerDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            TextButton(onClick = { onDateSelected(selectedDate) }) {
                ExpenseTextView(text = "Confirm", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                ExpenseTextView(text = "Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun TitleComponent(title: String) {
    ExpenseTextView(
        text = title.uppercase(),
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = LightGrey
    )
    Spacer(modifier = Modifier.size(10.dp))
}

@Composable
fun ExpenseDropDown(
    listOfItems: List<String>,
    selectedItem: String = listOfItems[0],
    onItemSelected: (item: String) -> Unit
) {
    val expanded = remember {
        mutableStateOf(false)
    }
    val currentSelected = remember(selectedItem) {
        mutableStateOf(selectedItem)
    }
    ExposedDropdownMenuBox(expanded = expanded.value, onExpandedChange = { expanded.value = it }) {
        OutlinedTextField(
            value = currentSelected.value,
            onValueChange = {},
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            textStyle = TextStyle(
                fontFamily = InterFontFamily,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            ),
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded.value)
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = androidx.compose.material3.MaterialTheme.colorScheme.outline,
                focusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            )
        )
        ExposedDropdownMenu(expanded = expanded.value, onDismissRequest = { expanded.value = false }) {
            listOfItems.forEach { item ->
                val isCustomItem = item.startsWith("+")
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCustomItem) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_edit),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    colorFilter = ColorFilter.tint(Zinc)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                ExpenseTextView(
                                    text = item,
                                    color = Zinc,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                ExpenseTextView(text = item)
                            }
                        }
                    },
                    onClick = {
                        if (!isCustomItem) {
                            currentSelected.value = item
                        }
                        onItemSelected(item)
                        expanded.value = false
                    }
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAddExpense() {
    AddExpense(rememberNavController(), true)
}

