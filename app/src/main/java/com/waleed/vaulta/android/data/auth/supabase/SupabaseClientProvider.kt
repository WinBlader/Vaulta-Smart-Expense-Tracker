package com.waleed.vaulta.android.data.auth.supabase

import com.waleed.vaulta.android.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.ktor.client.engine.okhttp.OkHttp

/**
 * Singleton provider for the official supabase-kt client.
 * Configured with Auth (with scheme & host for deep linking) and Postgrest plugins.
 */
object SupabaseClientProvider {

    val client: SupabaseClient by lazy {
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://xyzcompany.supabase.co" }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-anon-key" }

        createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key
        ) {
            httpEngine = OkHttp.create()

            install(Auth) {
                scheme = "vaulta"
                host = "login-callback"
                autoSaveToStorage = true
                autoLoadFromStorage = true
            }

            install(Postgrest) {
                defaultSchema = "public"
            }
        }
    }

    fun isConfigured(): Boolean {
        val url = BuildConfig.SUPABASE_URL
        val key = BuildConfig.SUPABASE_ANON_KEY
        return url.isNotBlank() &&
                !url.contains("xyzcompany") &&
                key.isNotBlank() &&
                !key.contains("placeholder")
    }
}
