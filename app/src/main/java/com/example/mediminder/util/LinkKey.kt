package com.example.mediminder.util

import android.content.Context
import android.content.Intent
import java.util.UUID

/**
 * MediMinder's own links (from its notifications, alarm and widget) carry a random key made
 * once per install. The app only follows links that carry it, so another app can't steer
 * MediMinder to a screen by sending it a link. The key is not backed up.
 */
object LinkKey {
    const val EXTRA = "com.example.mediminder.LINK_KEY"
    private const val FILE = "mediminder_links"

    @Volatile private var cached: String? = null

    fun get(context: Context): String = cached ?: synchronized(this) {
        cached ?: run {
            val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            prefs.getString("key", null) ?: UUID.randomUUID().toString().also { prefs.edit().putString("key", it).apply() }
        }.also { cached = it }
    }

    /** Marks [intent] as one of the app's own links. */
    fun sign(context: Context, intent: Intent): Intent = intent.putExtra(EXTRA, get(context))

    fun isOurs(context: Context, intent: Intent?): Boolean = intent?.getStringExtra(EXTRA) == get(context)
}
