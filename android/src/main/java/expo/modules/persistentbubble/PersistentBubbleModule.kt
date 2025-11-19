package expo.modules.persistentbubble

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class PersistentBubbleModule : Module() {
  private val context: Context
    get() = appContext.reactContext ?: appContext.currentActivity
    ?: throw IllegalStateException("Android context is not available yet")

  private var lastIconSource: String? = null
  private var lastIconSizeDp: Int? = null
  private var lastTrashIconSource: String? = null
  private var lastTrashIconSizeDp: Int? = null
  private var lastHidden: Boolean? = null
  private var lastTrashHidden: Boolean? = null
  companion object {
    private var state: String = ""
  }

  private fun buildConfigJson(): String {
    val sb = StringBuilder()
    sb.append('{')
    var first = true
    lastIconSizeDp?.let {
      if (!first) sb.append(',')
      sb.append("\"iconSizeDp\":").append(it)
      first = false
    }
    lastIconSource?.let {
      if (!first) sb.append(',')
      sb.append("\"iconSource\":\"")
        .append(it.replace("\\", "\\\\").replace("\"", "\\\""))
        .append("\"")
      first = false
    }
    lastTrashIconSizeDp?.let {
      if (!first) sb.append(',')
      sb.append("\"trashIconSizeDp\":").append(it)
      first = false
    }
    lastTrashIconSource?.let {
      if (!first) sb.append(',')
      sb.append("\"trashIconSource\":\"")
        .append(it.replace("\\", "\\\\").replace("\"", "\\\""))
        .append("\"")
      first = false
    }
    lastTrashHidden?.let {
      if (!first) sb.append(',')
      sb.append("\"hideTrash\":").append(if (it) "true" else "false")
    }
    lastHidden?.let {
      if (!first) sb.append(',')
      sb.append("\"hidden\":").append(if (it) "true" else "false")
    }
    sb.append('}')
    return sb.toString()
  }

  override fun definition() = ModuleDefinition {
    Name("PersistentBubble")

    Events("overlayActiveChanged", "iconRemoved", "overlayHiddenChanged")

    OnStartObserving {
      ActiveChangeNotifier.setListener { isActive ->
        try {
          sendEvent("overlayActiveChanged", mapOf("active" to isActive))
        } catch (_: Exception) { }
      }
      IconRemovedNotifier.setListener {
        try { sendEvent("iconRemoved", emptyMap<String, Any>()) } catch (_: Exception) { }
      }
      OverlayHiddenNotifier.setListener { hidden ->
        try { sendEvent("overlayHiddenChanged", mapOf("active" to hidden)) } catch (_: Exception) { }
      }
      // Emit the current state immediately for fresh subscribers
      try { sendEvent("overlayActiveChanged", mapOf("active" to FloatingIconService.isActive)) } catch (_: Exception) { }
    }

    OnStopObserving {
      ActiveChangeNotifier.setListener(null)
      IconRemovedNotifier.setListener(null)
      OverlayHiddenNotifier.setListener(null)
    }

    AsyncFunction("hasOverlayPermission") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
      } else {
        true
      }
    }

    AsyncFunction("openOverlaySettings") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val intent = Intent(
          Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
          Uri.parse("package:${context.packageName}")
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
      }
    }

    AsyncFunction("startOverlay") {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
        return@AsyncFunction null
      }
      val intent = Intent(context, FloatingIconService::class.java).apply {
        action = "CONFIG"
        putExtra("payload", buildConfigJson())
      }
      try { context.startService(intent) } catch (_: Exception) { }
    }

    AsyncFunction("stopOverlay") {
      val intent = Intent(context, FloatingIconService::class.java)
      try { context.stopService(intent) } catch (_: Exception) { }
    }

    AsyncFunction("setIcon") { source: String ->
      lastIconSource = source
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "CONFIG"
          putExtra("payload", buildConfigJson())
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    AsyncFunction("setIconSize") { dp: Double ->
      val sizeDp = dp.toInt().coerceAtLeast(1)
      lastIconSizeDp = sizeDp
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "CONFIG"
          putExtra("payload", buildConfigJson())
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    AsyncFunction("setTrashIcon") { source: String ->
      lastTrashIconSource = source
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "CONFIG"
          putExtra("payload", buildConfigJson())
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    // Reset main icon to default (remove custom source and size) and request immediate apply
    AsyncFunction("resetIcon") {
      lastIconSource = null
      lastIconSizeDp = null
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "RESET_ICON"
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    AsyncFunction("setTrashIconSize") { dp: Double ->
      val sizeDp = dp.toInt().coerceAtLeast(1)
      lastTrashIconSizeDp = sizeDp
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "CONFIG"
          putExtra("payload", buildConfigJson())
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    // Reset trash icon to default (remove custom trash source and size) and request immediate apply
    AsyncFunction("resetTrashIcon") {
      lastTrashIconSource = null
      lastTrashIconSizeDp = null
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "RESET_TRASH_ICON"
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    // Hide the overlay (1x1 transparent, touch disabled)
    AsyncFunction("hide") {
      // Persist desired hidden state so startOverlay can honor it even if called later
      lastHidden = true
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "HIDE"
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    // Show the overlay (restore previous visual and touch behavior)
    AsyncFunction("show") {
      // Persist desired hidden state so startOverlay can honor it
      lastHidden = false
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "SHOW"
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    AsyncFunction("setTrashHidden") { hidden: Boolean ->
      lastTrashHidden = hidden
      if (FloatingIconService.isActive) {
        val intent = Intent(context, FloatingIconService::class.java).apply {
          action = "CONFIG"
          putExtra("payload", buildConfigJson())
        }
        try { context.startService(intent) } catch (_: Exception) { }
      }
    }

    Function("isOverlayActive") {
      FloatingIconService.isActive
    }

    Function("isHidden") {
      // Prefer lastHidden (desired state persisted in module) falling back to the service runtime flag
      if (lastHidden != null) return@Function lastHidden
      try {
        return@Function FloatingIconService.isOverlayHidden
      } catch (_: Exception) {
        return@Function false
      }
    }
    
    Function("getState") {
      state
    }

    Function("setState") { value: String ->
      state = value
    }
  }
}
