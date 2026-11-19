package com.waleed.vaulta.android.utils

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.waleed.vaulta.android.R

object VaultaToast {

    fun show(context: Context, message: String, duration: Int = Toast.LENGTH_SHORT) {
        if (message.isBlank()) return

        try {
            // For devices below Android 11, use custom toast layout containing app icon
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                val inflater = LayoutInflater.from(context)
                val layout = inflater.inflate(R.layout.layout_custom_toast, null)
                val iconView = layout.findViewById<ImageView>(R.id.toast_icon)
                val textView = layout.findViewById<TextView>(R.id.toast_text)
                iconView.setImageResource(R.drawable.money_icon)
                textView.text = message

                val toast = Toast(context.applicationContext)
                toast.duration = duration
                toast.view = layout
                toast.show()
                return
            }
        } catch (_: Exception) {}

        // On Android 11+ (API 30+), standard Toast.makeText automatically renders the app's
        // launcher icon from mipmap/manifest alongside the message text.
        Toast.makeText(context, message, duration).show()
    }
}
