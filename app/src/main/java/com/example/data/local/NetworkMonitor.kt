package com.example.data.local

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

interface NetworkMonitor {
    fun isCurrentlyConnected(): Boolean
}

class AndroidNetworkMonitor(private val context: Context) : NetworkMonitor {
    override fun isCurrentlyConnected(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
