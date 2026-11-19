package com.waleed.vaulta.android.data.auth.supabase

/**
 * Configuration for Supabase Backend Authentication.
 * 
 * Replace SUPABASE_URL and SUPABASE_ANON_KEY with your project credentials
 * from your Supabase Dashboard: Settings -> API.
 */
object SupabaseConfig {
    // Replace with your Supabase Project URL (e.g., https://your-project-id.supabase.co)
    var SUPABASE_URL: String = "https://xyzcompany.supabase.co"

    // Replace with your Supabase public anon key (eyJhbGci...)
    var SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh5emNvbXBhbnkiLCJyb2xlIjoiYW5vbiIsImlhdCI6MTcwMDAwMDAwMCwiZXhwIjoyMDAwMDAwMDAwfQ.placeholder"

    fun isConfigured(): Boolean {
        return SUPABASE_URL.isNotBlank() &&
                !SUPABASE_URL.contains("xyzcompany") &&
                SUPABASE_ANON_KEY.isNotBlank() &&
                !SUPABASE_ANON_KEY.contains("placeholder")
    }
}
