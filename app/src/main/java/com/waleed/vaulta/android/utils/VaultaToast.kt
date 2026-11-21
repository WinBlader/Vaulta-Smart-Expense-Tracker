package com.waleed.vaulta.android.utils

import android.content.Context
import com.waleed.vaulta.android.widget.VaultaSnackbarController

object VaultaToast {

    /**
     * Shows a Vaulta-branded floating in-app banner with Vaulta's actual launcher icon.
     * Eliminates system Android robot icons displayed on Android 12+ standard toasts.
     */
    fun show(context: Context? = null, message: String, duration: Int = 0) {
        if (message.isBlank()) return
        VaultaSnackbarController.show(message = message, durationMs = 2800L)
    }

    /**
     * Shows a Vaulta-branded floating in-app banner with an interactive action button (e.g. "OPEN").
     */
    fun showWithAction(
        message: String,
        actionLabel: String,
        onAction: () -> Unit,
        durationMs: Long = 4500L
    ) {
        if (message.isBlank()) return
        VaultaSnackbarController.show(
            message = message,
            actionLabel = actionLabel,
            onAction = onAction,
            durationMs = durationMs
        )
    }
}
