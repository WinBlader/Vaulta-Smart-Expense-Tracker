package com.waleed.vaulta.android.feature.stats

import android.view.LayoutInflater
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.waleed.vaulta.android.R
import com.waleed.vaulta.android.feature.home.TransactionList
import com.waleed.vaulta.android.ui.theme.Green
import com.waleed.vaulta.android.ui.theme.LightGrey
import com.waleed.vaulta.android.ui.theme.Red
import com.waleed.vaulta.android.ui.theme.Zinc
import com.waleed.vaulta.android.utils.Utils
import com.waleed.vaulta.android.data.CurrencyManager
import com.waleed.vaulta.android.widget.ExpenseTextView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

@Composable
fun StatsScreen(
    navController: NavController,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val selectedType by viewModel.selectedType.collectAsState()
    val rawSummaries by viewModel.entries.collectAsState()
    val topExpense by viewModel.topEntries.collectAsState()
    val chartData = viewModel.getChartData(rawSummaries)

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
                        .clickable {
                            navController.navigateUp()
                        },
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                )
                ExpenseTextView(
                    text = "Statistics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.Center)
                )
                // Note: Three dots removed per user request
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Type toggle buttons: Expenses vs Income vs Credit / Owe
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                FilterTab(
                    title = "Expenses",
                    isSelected = selectedType == "Expense",
                    selectedColor = Red,
                    onClick = { viewModel.onTypeSelected("Expense") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterTab(
                    title = "Income",
                    isSelected = selectedType == "Income",
                    selectedColor = Green,
                    onClick = { viewModel.onTypeSelected("Income") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilterTab(
                    title = "Credit / Owe",
                    isSelected = selectedType == "Credit",
                    selectedColor = Color(0xFFF59E0B),
                    onClick = { viewModel.onTypeSelected("Credit") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Summary Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                if (selectedType == "Credit") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            ExpenseTextView(
                                text = "You Owe",
                                fontSize = 12.sp,
                                color = Color(0xFFEC4899),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            ExpenseTextView(
                                text = Utils.formatCurrency(chartData.totalAmount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEC4899)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            ExpenseTextView(
                                text = "Owed to You",
                                fontSize = 12.sp,
                                color = Color(0xFFA855F7),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            ExpenseTextView(
                                text = Utils.formatCurrency(chartData.secondaryTotalAmount),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFA855F7)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            ExpenseTextView(
                                text = "Total ${if (selectedType == "Income") "Earned" else "Spent"}",
                                fontSize = 12.sp,
                                color = LightGrey,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            ExpenseTextView(
                                text = Utils.formatCurrency(chartData.totalAmount),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedType == "Income") Green else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        ExpenseTextView(
                            text = "${chartData.entries.size} Record${if (chartData.entries.size != 1) "s" else ""}",
                            fontSize = 12.sp,
                            color = LightGrey
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Chart area
            if (chartData.entries.isEmpty() && chartData.secondaryEntries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_stats),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            colorFilter = ColorFilter.tint(LightGrey)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ExpenseTextView(
                            text = "No ${if (selectedType == "Credit") "credit / owe" else selectedType.lowercase()} data recorded yet.",
                            color = LightGrey,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                StatsLineChart(
                    entries = chartData.entries,
                    secondaryEntries = chartData.secondaryEntries,
                    labels = chartData.labels,
                    chartMode = selectedType
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dynamic Top List (Top Spending, Top Earning, Top Credit)
            val listTitle = when (selectedType) {
                "Income" -> "Top Earning"
                "Credit" -> "Top Credit"
                else -> "Top Spending"
            }
            TransactionList(
                modifier = Modifier.fillMaxWidth(),
                list = topExpense,
                title = listTitle,
                onSeeAllClicked = {
                    navController.navigate("/all_transactions")
                }
            )
        }
    }
}

@Composable
fun FilterTab(
    title: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) selectedColor.copy(alpha = 0.15f) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        ExpenseTextView(
            text = title,
            color = if (isSelected) selectedColor else LightGrey,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun StatsLineChart(
    entries: List<Entry>,
    secondaryEntries: List<Entry> = emptyList(),
    labels: List<String>,
    chartMode: String
) {
    val context = LocalContext.current
    val isCredit = chartMode == "Credit"
    val isIncome = chartMode == "Income"

    val primaryColor = when {
        isCredit -> android.graphics.Color.parseColor("#EC4899") // Pink for "You Owe"
        isIncome -> android.graphics.Color.parseColor("#2E7D32")
        else -> android.graphics.Color.parseColor("#2F7E79")
    }
    val secondaryColor = android.graphics.Color.parseColor("#A855F7") // Purple for "Owed to You"

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (isCredit) {
            // Legend for Credit & Owe dual line chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFEC4899), shape = RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                ExpenseTextView(
                    text = "You Owe",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFEC4899)
                )

                Spacer(modifier = Modifier.width(20.dp))

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(Color(0xFFA855F7), shape = RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                ExpenseTextView(
                    text = "Owed to You",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFA855F7)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        AndroidView(
            factory = {
                val view = LayoutInflater.from(context).inflate(R.layout.stats_line_chart, null)
                view
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            update = { view ->
                try {
                    val lineChart = view.findViewById<LineChart>(R.id.lineChart)

                    lineChart.description.isEnabled = false
                    lineChart.legend.isEnabled = false
                    lineChart.setTouchEnabled(true)
                    lineChart.isDragEnabled = true
                    lineChart.setScaleEnabled(false)
                    lineChart.setPinchZoom(false)
                    lineChart.setDrawGridBackground(false)

                    val dataSets = mutableListOf<com.github.mikephil.charting.interfaces.datasets.ILineDataSet>()

                    if (entries.isNotEmpty()) {
                        val primaryLabel = if (isCredit) "You Owe" else if (isIncome) "Income" else "Expenses"
                        val dataSet1 = LineDataSet(entries, primaryLabel).apply {
                            color = primaryColor
                            lineWidth = 3f
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            axisDependency = YAxis.AxisDependency.LEFT
                            setDrawCircles(true)
                            circleRadius = 5f
                            setCircleColor(primaryColor)
                            setDrawCircleHole(true)
                            circleHoleRadius = 2.5f
                            circleHoleColor = android.graphics.Color.WHITE
                            setDrawValues(true)
                            valueTextSize = 10f
                            valueTextColor = primaryColor
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return "${CurrencyManager.getSelectedCurrencySymbol()}${value.toInt()}"
                                }
                            }
                            setDrawFilled(!isCredit)
                            if (!isCredit) {
                                val drawable = ContextCompat.getDrawable(context, R.drawable.char_gradient)
                                drawable?.let { fillDrawable = it }
                            }
                        }
                        dataSets.add(dataSet1)
                    }

                    if (isCredit && secondaryEntries.isNotEmpty()) {
                        val dataSet2 = LineDataSet(secondaryEntries, "Owed to You").apply {
                            color = secondaryColor
                            lineWidth = 3f
                            mode = LineDataSet.Mode.CUBIC_BEZIER
                            axisDependency = YAxis.AxisDependency.LEFT
                            setDrawCircles(true)
                            circleRadius = 5f
                            setCircleColor(secondaryColor)
                            setDrawCircleHole(true)
                            circleHoleRadius = 2.5f
                            circleHoleColor = android.graphics.Color.WHITE
                            setDrawValues(true)
                            valueTextSize = 10f
                            valueTextColor = secondaryColor
                            valueFormatter = object : ValueFormatter() {
                                override fun getFormattedValue(value: Float): String {
                                    return "${CurrencyManager.getSelectedCurrencySymbol()}${value.toInt()}"
                                }
                            }
                            setDrawFilled(false)
                        }
                        dataSets.add(dataSet2)
                    }

                    // Left Y-Axis
                    lineChart.axisLeft.apply {
                        isEnabled = true
                        setDrawGridLines(true)
                        gridColor = android.graphics.Color.parseColor("#E8EAEA")
                        textColor = android.graphics.Color.GRAY
                        axisMinimum = 0f
                        setLabelCount(4, true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val s = CurrencyManager.getSelectedCurrencySymbol(); return if (value >= 1000) "$s${(value / 1000).toInt()}k" else "$s${value.toInt()}"
                            }
                        }
                    }

                    // Right Y-Axis (disabled)
                    lineChart.axisRight.isEnabled = false

                    // X-Axis
                    lineChart.xAxis.apply {
                        isEnabled = true
                        position = XAxis.XAxisPosition.BOTTOM
                        setDrawGridLines(false)
                        setDrawAxisLine(true)
                        axisLineColor = android.graphics.Color.parseColor("#E8EAEA")
                        textColor = android.graphics.Color.GRAY
                        granularity = 1f
                        setLabelCount(minOf(labels.size, 5).coerceAtLeast(1), true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val index = value.toInt()
                                return if (index in labels.indices) labels[index] else ""
                            }
                        }
                        val effectiveCount = maxOf(entries.size, secondaryEntries.size)
                        if (effectiveCount <= 1) {
                            axisMinimum = -0.5f
                            axisMaximum = 1f
                        } else {
                            axisMinimum = -0.2f
                            axisMaximum = (effectiveCount - 0.8f).coerceAtLeast(1f)
                        }
                    }

                    if (dataSets.isNotEmpty()) {
                        lineChart.data = LineData(dataSets)
                        lineChart.notifyDataSetChanged()
                        lineChart.invalidate()
                    } else {
                        lineChart.clear()
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("StatsLineChart", "Safe catch during chart update", t)
                }
            }
        )
    }
}
