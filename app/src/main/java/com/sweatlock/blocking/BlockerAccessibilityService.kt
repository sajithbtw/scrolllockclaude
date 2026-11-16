package com.sweatlock.blocking

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.sweatlock.data.AppSettings
import com.sweatlock.data.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BlockerEntryPoint {
    fun settings(): SettingsRepository
    fun unlocks(): UnlockManager
}

/**
 * Watches which app comes to the front. If it is a blocked app and its unlock timer is not running:
 *  1. instantly covers it with a lock cover (accessibility overlay, no permission games), and
 *  2. opens LockActivity (the camera task screen) on top.
 * If step 2 is ever blocked by the phone, the cover still shows a "Start task" button, so the app is never left open.
 * A 1s ticker re-locks when a timer expires while you're still inside the app.
 */
class BlockerAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var unlocks: UnlockManager
    @Volatile private var cfg = AppSettings()
    private var safe: Set<String> = emptySet()
    private var currentPkg: String? = null
    private var lastLaunch = 0L

    private var cover: View? = null
    private var coverPkg: String? = null
    private var coverShownAt = 0L

    override fun onServiceConnected() {
        val ep = EntryPointAccessors.fromApplication(applicationContext, BlockerEntryPoint::class.java)
        unlocks = ep.unlocks()
        safe = SafeApps.compute(this)
        scope.launch { unlocks.load() }
        scope.launch { ep.settings().settings.collect { cfg = it } }
        scope.launch {
            var n = 0
            while (isActive) {
                delay(1000)
                if (++n % 60 == 0) safe = SafeApps.compute(this@BlockerAccessibilityService)
                tick()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return

        if (pkg == packageName) {
            currentPkg = pkg
            // Our own lock screen is up: the cover has done its job (ignore events caused by the cover itself).
            if (cover != null && SystemClock.elapsedRealtime() - coverShownAt > 1000) hideCover()
            return
        }
        // Notification shade / keyboard windows are not "the app the user is in".
        if (pkg == "com.android.systemui" || pkg.contains("inputmethod")) return

        currentPkg = pkg
        if (cover != null && pkg != coverPkg) hideCover()
        checkAndLock(pkg)
    }

    private fun tick() {
        val c = coverPkg
        if (cover != null && c != null && (!cfg.enabled || unlocks.isUnlocked(c))) hideCover()
        checkAndLock(currentPkg ?: Perms.foregroundFromUsage(this))
    }

    private fun checkAndLock(pkg: String?) {
        if (pkg == null || !::unlocks.isInitialized) return
        if (!cfg.enabled || pkg !in cfg.blocked) return
        if (pkg in safe || SafeApps.looksCritical(pkg)) return   // never lock phone, settings, home, emergency
        if (unlocks.isUnlocked(pkg)) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastLaunch < 1500) return                       // debounce
        lastLaunch = now
        showCover(pkg)
        launchLock(pkg)
    }

    private fun launchLock(pkg: String) {
        runCatching {
            startActivity(
                Intent(this, LockActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(LockActivity.EXTRA_PKG, pkg)
            )
        }
    }

    // ---- lock cover (plain Views, no theme/Compose needed) --------------------------------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 17f
        gravity = Gravity.CENTER
        setPadding(dp(36), dp(14), dp(36), dp(14))
        background = GradientDrawable().apply {
            cornerRadius = dp(50).toFloat()
            setColor(if (primary) 0xFF0A84FF.toInt() else 0x33FFFFFF)
        }
        setOnClickListener { onClick() }
    }

    private fun goHomeAndHide() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        hideCover()
    }

    private fun showCover(pkg: String) {
        if (cover != null) return
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("This app")

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xF20A0F2E.toInt())
            setPadding(dp(32), dp(32), dp(32), dp(32))
            isClickable = true // swallow touches so the app underneath can't be used
            isFocusableInTouchMode = true
            addView(TextView(context).apply {
                text = "\uD83D\uDD12 $label is locked"
                setTextColor(Color.WHITE); textSize = 26f; gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = "Finish your task to unlock it."
                setTextColor(0xB3FFFFFF.toInt()); textSize = 16f; gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(28))
            })
            addView(pill("Start task", true) { launchLock(pkg) })
            addView(View(context), LinearLayout.LayoutParams(1, dp(14)))
            addView(pill("Go home", false) { goHomeAndHide() })
            setOnKeyListener { _, code, ev ->
                if (code == KeyEvent.KEYCODE_BACK && ev.action == KeyEvent.ACTION_UP) { goHomeAndHide(); true } else false
            }
        }
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        runCatching {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(root, lp)
            cover = root; coverPkg = pkg; coverShownAt = SystemClock.elapsedRealtime()
            root.requestFocus()
        }
    }

    private fun hideCover() {
        cover?.let { v -> runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) } }
        cover = null; coverPkg = null
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideCover()
        scope.cancel()
        super.onDestroy()
    }
}
