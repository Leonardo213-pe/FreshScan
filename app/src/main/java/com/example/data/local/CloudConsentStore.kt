package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

interface CloudConsentStore {
    fun hasAcceptedCloudProcessing(): Boolean
    fun setAcceptedCloudProcessing(accepted: Boolean)
}

class SharedPrefsCloudConsentStore(context: Context) : CloudConsentStore {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun hasAcceptedCloudProcessing(): Boolean {
        return prefs.getBoolean(KEY_CONSENT_ACCEPTED, false)
    }

    override fun setAcceptedCloudProcessing(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_ACCEPTED, accepted).apply()
    }

    companion object {
        private const val PREFS_NAME = "freshscan_privacy_prefs"
        private const val KEY_CONSENT_ACCEPTED = "cloud_processing_consent_v1"
    }
}
