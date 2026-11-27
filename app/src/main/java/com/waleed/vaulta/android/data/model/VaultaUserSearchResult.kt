package com.waleed.vaulta.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VaultaUserSearchResult(
    @SerialName("user_id") val userId: String = "",
    @SerialName("display_name") val displayName: String = "",
    @SerialName("masked_contact") val maskedContact: String? = null
)
