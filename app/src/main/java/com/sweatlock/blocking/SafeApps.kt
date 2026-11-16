package com.sweatlock.blocking

import android.content.Context
import android.content.Intent
import android.telecom.TelecomManager

/**
 * Safety net for a main phone: these apps can never be locked or picked,
 * so you can always call, reach emergency services, open Settings (to turn SweatLock off)
 * and get back to your home screen.
 */
object SafeApps {
    private val STATIC = setOf(
        "com.android.settings", "com.android.systemui", "com.android.phone",
        "com.android.server.telecom", "com.android.incallui", "com.android.emergency",
        "com.google.android.dialer", "com.android.dialer",
        "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller"
    )

    fun looksCritical(pkg: String) =
        pkg.contains("incallui") || pkg.contains("telecom") || pkg.contains("emergency") ||
            pkg.contains("permissioncontroller") || pkg.contains("packageinstaller")

    /** Static list + SweatLock itself + your default dialer + your home screen app(s). */
    fun compute(ctx: Context): Set<String> {
        val s = STATIC.toMutableSet()
        s += ctx.packageName
        runCatching { ctx.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let { s += it } }
        runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            ctx.packageManager.queryIntentActivities(home, 0).forEach { s += it.activityInfo.packageName }
        }
        return s
    }
}
