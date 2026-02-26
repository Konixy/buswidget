package com.buswidget.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat

object BatteryOptimizationHelper {

    /**
     * Vérifie si l'app est déjà exemptée d'optimisation batterie.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Retourne l'intent pour afficher la popup système d'exemption.
     * La popup est native Android, on ne peut pas la personnaliser.
     */
    fun buildRequestIntent(context: Context): Intent {
        return Intent(
            android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
    }
}
