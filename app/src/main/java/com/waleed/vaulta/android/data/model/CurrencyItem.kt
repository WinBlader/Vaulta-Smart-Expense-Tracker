package com.waleed.vaulta.android.data.model

data class CurrencyItem(
    val code: String,
    val symbol: String,
    val name: String,
    val country: String,
    val flag: String,
    val category: String = "Other"
)
