package com.sweatlock.blocking

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppInfo(val pkg: String, val label: String, val icon: ImageBitmap)

@Singleton
class InstalledAppsProvider @Inject constructor(@ApplicationContext private val ctx: Context) {
    /** Launchable apps only (what a person can actually doomscroll in). */
    suspend fun load(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = ctx.packageManager
        val safe = SafeApps.compute(ctx)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it !in safe && !SafeApps.looksCritical(it) }
            .mapNotNull { pkg ->
                runCatching {
                    AppInfo(
                        pkg,
                        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(),
                        pm.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap()
                    )
                }.getOrNull()
            }
            .sortedBy { it.label.lowercase() }
    }
}
