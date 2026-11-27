package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ContactLookupResult(
    val found: Boolean = false,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("display_name") val displayName: String? = null
)
