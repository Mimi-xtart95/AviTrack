package org.avitrack.app.data.remote

object SupabaseConfig {
    private const val URL_PLACEHOLDER = "YOUR_SUPABASE_URL"
    private const val KEY_PLACEHOLDER = "YOUR_SUPABASE_ANON_KEY"

    const val SUPABASE_URL = "https://jtonxplcyfnkxrncywkv.supabase.co"
    // Use the public anon key with Row Level Security enabled.
    const val SUPABASE_ANON_KEY = "sb_publishable_fPkiegMdPx7lPPYQ40zycQ_ABDWVg3a"

    val isConfigured: Boolean
        get() = SUPABASE_URL.isNotBlank() && SUPABASE_URL.trim() != URL_PLACEHOLDER &&
            SUPABASE_ANON_KEY.isNotBlank() && SUPABASE_ANON_KEY.trim() != KEY_PLACEHOLDER
}
