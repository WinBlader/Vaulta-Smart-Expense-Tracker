package com.waleed.vaulta.android.data.model

data class UserProfile(
    val id: String = "",
    val name: String,
    val email: String,
    val phoneNumber: String,
    val authProvider: String = "Supabase Auth (Verified)",
    val isVerified: Boolean = true,
    val registeredAt: Long = System.currentTimeMillis()
)
