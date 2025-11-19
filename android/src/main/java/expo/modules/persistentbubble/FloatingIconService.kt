package expo.modules.persistentbubble

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import kotlin.math.abs

// Import module R from the library namespace
import expo.modules.persistentbubble.R
import expo.modules.persistentbubble.ActiveChangeNotifier
import expo.modules.persistentbubble.IconRemovedNotifier

class FloatingIconService : Service() {
  companion object {
    @JvmStatic
    var isActive: Boolean = false
    @JvmStatic
    var isOverlayHidden: Boolean = false
  }

  private lateinit var windowManager: WindowManager
  private lateinit var floatingIconView: View
  private lateinit var trashView: View
  private lateinit var params: WindowManager.LayoutParams
  private lateinit var trashParams: WindowManager.LayoutParams
  private var trashIcon: ImageView? = null
  private var trashHitbox: View? = null
  private var isHoveringTrash: Boolean = false
  private var snapAnimator: ValueAnimator? = null
  private var viewAdded: Boolean = false
  private var hideTrash: Boolean = false
  private var savedImageLp: ViewGroup.LayoutParams? = null
  private var savedParamsFlags: Int = 0
  private var isHiddenOverlay: Boolean = false
  private var savedParamsWidth: Int = WindowManager.LayoutParams.WRAP_CONTENT
  private var savedParamsHeight: Int = WindowManager.LayoutParams.WRAP_CONTENT

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    val inflater = LayoutInflater.from(this)
    floatingIconView = inflater.inflate(R.layout.layout_floating_icon, null)
    trashView = inflater.inflate(R.layout.layout_trash_can, null)
    // Resolve references immediately
    trashIcon = trashView.findViewById(R.id.trash_icon)
    trashHitbox = trashView.findViewById(R.id.trash_hitbox)

    val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
      @Suppress("DEPRECATION")
      WindowManager.LayoutParams.TYPE_PHONE
    }

    params = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
    }

    trashParams = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      layoutFlag,
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER
    }

    setupTouchListener()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!viewAdded) {
      val dm = resources.displayMetrics
      val payload = intent?.getStringExtra("payload")
      var shouldStartHidden = false
      if (!payload.isNullOrEmpty()) {
        try {
          val obj = org.json.JSONObject(payload)
          val size = if (obj.has("iconSizeDp") && !obj.isNull("iconSizeDp")) obj.optInt("iconSizeDp", -1) else -1
          if (size > 0) applyIconSizeDp(size)
          val source = obj.optString("iconSource", "")
          if (source.isNotEmpty()) applyIconFromSource(source)
          val tSize = if (obj.has("trashIconSizeDp") && !obj.isNull("trashIconSizeDp")) obj.optInt("trashIconSizeDp", -1) else -1
          if (tSize > 0) applyTrashIconSizeDp(tSize)
          val tSource = obj.optString("trashIconSource", "")
          if (tSource.isNotEmpty()) applyTrashIconFromSource(tSource)
          if (obj.has("hideTrash") && !obj.isNull("hideTrash")) hideTrash = obj.optBoolean("hideTrash", false)
          if (obj.has("hidden") && !obj.isNull("hidden")) shouldStartHidden = obj.optBoolean("hidden", false)
        } catch (_: Exception) {}
      }

      floatingIconView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      )
      val defaultSize = (64 * dm.density).toInt()
      val inset = (16 * dm.density).toInt()
      val iconW = if (floatingIconView.measuredWidth > 0) floatingIconView.measuredWidth else defaultSize
      val iconH = if (floatingIconView.measuredHeight > 0) floatingIconView.measuredHeight else defaultSize
      val saved = loadSavedPositionOrRatio(iconW, iconH)
      if (saved != null) {
        val maxX = (dm.widthPixels - iconW).coerceAtLeast(0)
        val maxY = (dm.heightPixels - iconH).coerceAtLeast(0)
        params.x = saved.first.coerceIn(0, maxX)
        params.y = saved.second.coerceIn(0, maxY)
      } else {
        params.x = (dm.widthPixels - iconW - inset).coerceAtLeast(0)
        params.y = ((dm.heightPixels - iconH) / 2).coerceAtLeast(0)
      }

      try {
        windowManager.addView(floatingIconView, params)
        viewAdded = true
        isActive = true
        ActiveChangeNotifier.notify(true)
        if (saved == null) {
          // Persist initial position so a quick hide/show restores this spot
          savePositionEnhanced(params.x, params.y)
        }
        if (shouldStartHidden) {
          try { hideOverlay() } catch (_: Exception) { }
        }
      } catch (_: Exception) { }
    }

    when (intent?.action) {
      "CONFIG" -> {
        val json = intent.getStringExtra("payload")
        if (!json.isNullOrEmpty()) {
          try {
            val obj = org.json.JSONObject(json)
            val size = if (obj.has("iconSizeDp") && !obj.isNull("iconSizeDp")) obj.optInt("iconSizeDp", -1) else -1
            if (size > 0) applyIconSizeDp(size)
            val source = obj.optString("iconSource", "")
            if (source.isNotEmpty()) applyIconFromSource(source)
            val tSize = if (obj.has("trashIconSizeDp") && !obj.isNull("trashIconSizeDp")) obj.optInt("trashIconSizeDp", -1) else -1
            if (tSize > 0) applyTrashIconSizeDp(tSize)
            val tSource = obj.optString("trashIconSource", "")
            if (tSource.isNotEmpty()) applyTrashIconFromSource(tSource)
            if (obj.has("hideTrash") && !obj.isNull("hideTrash")) hideTrash = obj.optBoolean("hideTrash", false)
            if (obj.has("hidden") && !obj.isNull("hidden")) {
              val shouldHide = obj.optBoolean("hidden", false)
              if (shouldHide) {
                try { hideOverlay() } catch (_: Exception) { }
              } else {
                try { showOverlay() } catch (_: Exception) { }
              }
            }
          } catch (_: Exception) { }
        }
      }
      "RESET_ICON" -> {
        try { resetIconToDefault() } catch (_: Exception) { }
      }
      "RESET_TRASH_ICON" -> {
        try { resetTrashIconToDefault() } catch (_: Exception) { }
      }
      "HIDE" -> {
        try { hideOverlay() } catch (_: Exception) { }
      }
      "SHOW" -> {
        try { showOverlay() } catch (_: Exception) { }
      }
    }
    return START_STICKY
  }

  private fun hideOverlay() {
    if (!::floatingIconView.isInitialized || !viewAdded || isHiddenOverlay) return
    try {
      // Save current params width/height and flags
      savedParamsWidth = params.width
      savedParamsHeight = params.height
      savedParamsFlags = params.flags
      // Make the overlay visually transparent and 1x1
      params.width = 1
      params.height = 1
      floatingIconView.alpha = 0f
      // Disable touch on the overlay
      params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      try { windowManager.updateViewLayout(floatingIconView, params) } catch (_: Exception) { }
      isHiddenOverlay = true
      isOverlayHidden = true
      try { OverlayHiddenNotifier.notifyHidden(true) } catch (_: Exception) { }
    } catch (_: Exception) { }
  }

  private fun showOverlay() {
    if (!::floatingIconView.isInitialized || !viewAdded || !isHiddenOverlay) return
    try {
      // Restore params width/height and flags
      params.width = savedParamsWidth
      params.height = savedParamsHeight
      floatingIconView.alpha = 1f
      params.flags = savedParamsFlags
      try { windowManager.updateViewLayout(floatingIconView, params) } catch (_: Exception) { }
      isHiddenOverlay = false
      isOverlayHidden = false
      try { OverlayHiddenNotifier.notifyHidden(false) } catch (_: Exception) { }
    } catch (_: Exception) { }
  }

  private fun resetIconToDefault() {
    try {
      val imageView = floatingIconView.findViewById<ImageView>(R.id.floating_icon_image) ?: return
      // Try to resolve module/app mipmap launcher resource at runtime, fallback to system default if missing
      val resId = resources.getIdentifier("ic_launcher", "mipmap", packageName)
      if (resId != 0) {
        imageView.setImageResource(resId)
      } else {
        imageView.setImageResource(android.R.drawable.sym_def_app_icon)
      }
      // Reset visual size to default common size (64dp) so reset also reverts size
      try { applyIconSizeDp(64) } catch (_: Exception) { }
      try { windowManager.updateViewLayout(floatingIconView, params) } catch (_: Exception) { }
    } catch (_: Exception) { }
  }

  private fun resetTrashIconToDefault() {
    try {
      trashIcon?.setImageResource(android.R.drawable.ic_menu_delete)
      // Reset trash icon size to layout default (70dp as defined in layout_trash_can.xml)
      try { applyTrashIconSizeDp(70) } catch (_: Exception) { }
      try { if (trashView.windowToken != null) windowManager.updateViewLayout(trashView, trashParams) } catch (_: Exception) { }
    } catch (_: Exception) { }
  }

  private fun setupTouchListener() {
    floatingIconView.setOnTouchListener(object : View.OnTouchListener {
      private var initialX = 0
      private var initialY = 0
      private var initialTouchX = 0f
      private var initialTouchY = 0f

      private fun isAClick(startX: Float, endX: Float, startY: Float, endY: Float): Boolean {
        val differenceX = abs(startX - endX)
        val differenceY = abs(startY - endY)
        return differenceX <= 5 && differenceY <= 5
      }

      override fun onTouch(v: View?, event: MotionEvent): Boolean {
        when (event.action) {
          MotionEvent.ACTION_DOWN -> {
            initialX = params.x
            initialY = params.y
            initialTouchX = event.rawX
            initialTouchY = event.rawY
            showTrashCan()
            return true
          }
          MotionEvent.ACTION_MOVE -> {
            params.x = initialX + (event.rawX - initialTouchX).toInt()
            params.y = initialY + (event.rawY - initialTouchY).toInt()
            windowManager.updateViewLayout(floatingIconView, params)
            updateTrashHoverState()
            return true
          }
          MotionEvent.ACTION_UP -> {
            val inTrash = isViewInTrash()
            hideTrashCan(animate = !inTrash)
            if (inTrash) {
              try { IconRemovedNotifier.notifyRemoved() } catch (_: Exception) {}
              stopSelf()
            } else if (isAClick(initialTouchX, event.rawX, initialTouchY, event.rawY)) {
              packageManager.getLaunchIntentForPackage(packageName)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                startActivity(it)
              }
            } else {
              val finalX = snapToEdge()
              // Persist the final snapped position immediately
              savePositionEnhanced(finalX, params.y)
            }
            return true
          }
        }
        return false
      }
    })
  }

  private fun isViewInTrash(): Boolean {
    if (hideTrash) return false
    val hitbox = trashHitbox ?: return false
    val loc = IntArray(2)
    hitbox.getLocationOnScreen(loc)
    val left = loc[0]
    val top = loc[1]
    val right = left + hitbox.width
    val bottom = top + hitbox.height

    val iconLoc = IntArray(2)
    floatingIconView.getLocationOnScreen(iconLoc)
    val iconCenterX = iconLoc[0] + (floatingIconView.width / 2)
    val iconCenterY = iconLoc[1] + (floatingIconView.height / 2)

    return iconCenterX in left..right && iconCenterY in top..bottom
  }

  private fun showTrashCan() {
    if (hideTrash) return
    if (trashView.windowToken == null) {
      trashView.animate().cancel()
      trashView.alpha = 0f
      windowManager.addView(trashView, trashParams)
      isHoveringTrash = false
      trashIcon?.apply { scaleX = 1f; scaleY = 1f }
      trashHitbox?.visibility = View.INVISIBLE
      trashView.animate().alpha(1f).setDuration(180).start()
    }
  }

  private fun updateTrashHoverState() {
    val hovering = isViewInTrash()
    if (hovering != isHoveringTrash) {
      isHoveringTrash = hovering
      if (hovering) {
        trashHitbox?.visibility = View.VISIBLE
        trashHitbox?.isActivated = true
        trashIcon?.animate()?.scaleX(1.15f)?.scaleY(1.15f)?.setDuration(120)?.start()
      } else {
        trashHitbox?.visibility = View.INVISIBLE
        trashHitbox?.isActivated = false
        trashIcon?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
      }
    }
  }

  private fun hideTrashCan(animate: Boolean = true) {
    if (trashView.windowToken != null) {
      trashView.animate().cancel()
      if (animate) {
        trashView.animate()
          .alpha(0f)
          .setDuration(150)
          .withEndAction {
            try { if (trashView.windowToken != null) windowManager.removeView(trashView) } catch (_: Exception) {}
            trashView.alpha = 1f
            isHoveringTrash = false
          }
          .start()
      } else {
        try { windowManager.removeView(trashView) } catch (_: Exception) {}
        isHoveringTrash = false
        trashView.alpha = 1f
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    if (::floatingIconView.isInitialized && viewAdded) {
      try { windowManager.removeView(floatingIconView) } catch (_: Exception) { }
    }
    hideTrashCan(animate = false)
    isActive = false
    ActiveChangeNotifier.notify(false)
    viewAdded = false
  }

  private fun applyIconFromSource(source: String) {
    val imageView = floatingIconView.findViewById<ImageView>(R.id.floating_icon_image) ?: return
    try {
      when {
        source.startsWith("data:") -> {
          val base64Part = source.substringAfter(',')
          val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
          val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          imageView.setImageBitmap(bmp)
        }
        source.startsWith("file:") || source.startsWith("content:") -> {
          val uri = android.net.Uri.parse(source)
          contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            imageView.setImageBitmap(bmp)
          }
        }
        else -> {
          val bmp = android.graphics.BitmapFactory.decodeFile(source)
          if (bmp != null) imageView.setImageBitmap(bmp)
        }
      }
    } catch (_: Exception) { }
  }

  private fun applyIconSizeDp(sizeDp: Int) {
    val imageView = floatingIconView.findViewById<ImageView>(R.id.floating_icon_image) ?: return
    val px = ((sizeDp * resources.displayMetrics.density).toInt()).coerceAtLeast(1)
    var lp = imageView.layoutParams
    if (lp == null) {
      lp = ViewGroup.LayoutParams(px, px)
    } else {
      lp.width = px
      lp.height = px
    }
    imageView.layoutParams = lp
    imageView.scaleType = ImageView.ScaleType.FIT_XY
    try { windowManager.updateViewLayout(floatingIconView, params) } catch (_: Exception) { }
  }

  private fun applyTrashIconFromSource(source: String) {
    val imageView = trashIcon ?: return
    try {
      when {
        source.startsWith("data:") -> {
          val base64Part = source.substringAfter(',')
          val bytes = android.util.Base64.decode(base64Part, android.util.Base64.DEFAULT)
          val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
          imageView.setImageBitmap(bmp)
        }
        source.startsWith("file:") || source.startsWith("content:") -> {
          val uri = android.net.Uri.parse(source)
          contentResolver.openInputStream(uri)?.use { stream ->
            val bmp = android.graphics.BitmapFactory.decodeStream(stream)
            imageView.setImageBitmap(bmp)
          }
        }
        else -> {
          val bmp = android.graphics.BitmapFactory.decodeFile(source)
          if (bmp != null) imageView.setImageBitmap(bmp)
        }
      }
    } catch (_: Exception) { }
  }

  private fun applyTrashIconSizeDp(sizeDp: Int) {
    val imageView = trashIcon ?: return
    val px = ((sizeDp * resources.displayMetrics.density).toInt()).coerceAtLeast(1)
    var lp = imageView.layoutParams
    if (lp == null) {
      lp = ViewGroup.LayoutParams(px, px)
    } else {
      lp.width = px
      lp.height = px
    }
    imageView.layoutParams = lp
    imageView.scaleType = ImageView.ScaleType.FIT_XY
    // Update the visual hitbox to scale with the trash icon size so dropping feels consistent.
    // Compute a hitbox dp that's at least double the icon or icon + 24dp padding (whichever is larger).
    val hitboxDp = kotlin.math.max(sizeDp * 2, sizeDp + 24)
    val hitboxPx = dpToPx(hitboxDp)
    trashHitbox?.let { hb ->
      var hlp = hb.layoutParams
      if (hlp == null) {
        hlp = ViewGroup.LayoutParams(hitboxPx, hitboxPx)
      } else {
        hlp.width = hitboxPx
        hlp.height = hitboxPx
      }
      hb.layoutParams = hlp
    }
    try { if (trashView.windowToken != null) windowManager.updateViewLayout(trashView, trashParams) } catch (_: Exception) { }
  }

  private fun dpToPx(dp: Int): Int {
    val density = resources.displayMetrics.density
    return (dp * density).toInt()
  }

  private fun snapToEdge(): Int {
    val dm = resources.displayMetrics
    val margin = dpToPx(16).coerceAtLeast(0)
    var iconW = floatingIconView.width
    if (iconW == 0) {
      floatingIconView.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
      )
      iconW = if (floatingIconView.measuredWidth > 0) floatingIconView.measuredWidth else dpToPx(64)
    }
    val currentX = params.x
    val midX = currentX + iconW / 2
    val screenMidX = dm.widthPixels / 2
    val targetX = if (midX < screenMidX) margin else (dm.widthPixels - iconW - margin)
    if (currentX == targetX) return targetX
    snapAnimator?.cancel()
    snapAnimator = ValueAnimator.ofInt(currentX, targetX).apply {
      duration = 220
      interpolator = DecelerateInterpolator()
      addUpdateListener { animator ->
        params.x = animator.animatedValue as Int
        windowManager.updateViewLayout(floatingIconView, params)
      }
      start()
    }
    return targetX
  }

  // JS now controls auto-hide behavior via AppState events; native no-op here.

  private fun loadSavedPositionOrRatio(iconW: Int, iconH: Int): Pair<Int, Int>? {
    return try {
      val prefs = getSharedPreferences("persistent_bubble", MODE_PRIVATE)
      val dm = resources.displayMetrics
      val hasRatio = prefs.contains("lastYRatio") && prefs.contains("lastEdge")
      if (hasRatio) {
        val ratio = prefs.getFloat("lastYRatio", 0.5f).coerceIn(0f, 1f)
        val edge = prefs.getString("lastEdge", "right") ?: "right"
        val margin = dpToPx(16)
        val maxY = (dm.heightPixels - iconH).coerceAtLeast(0)
        val y = (ratio * maxY).toInt().coerceIn(0, maxY)
        val x = if (edge == "left") margin else (dm.widthPixels - iconW - margin).coerceAtLeast(0)
        Pair(x, y)
      } else {
        val x = prefs.getInt("lastX", Int.MIN_VALUE)
        val y = prefs.getInt("lastY", Int.MIN_VALUE)
        if (x != Int.MIN_VALUE && y != Int.MIN_VALUE) {
          // Derive edge and ratio from legacy x/y and persist them for rotation-aware behavior
          val margin = dpToPx(16)
          val leftX = margin
          val rightX = (dm.widthPixels - iconW - margin).coerceAtLeast(0)
          val edge = if (kotlin.math.abs(x - leftX) <= kotlin.math.abs(x - rightX)) "left" else "right"
          val maxY = (dm.heightPixels - iconH).coerceAtLeast(1)
          val ratio = (y.toFloat() / maxY.toFloat()).coerceIn(0f, 1f)
          getSharedPreferences("persistent_bubble", MODE_PRIVATE)
            .edit()
            .putString("lastEdge", edge)
            .putFloat("lastYRatio", ratio)
            .apply()
          // Return adjusted x using edge to keep consistent placement on wider screens
          val adjX = if (edge == "left") leftX else rightX
          Pair(adjX, (ratio * (dm.heightPixels - iconH).coerceAtLeast(0)).toInt())
        } else null
      }
    } catch (_: Exception) { null }
  }

  private fun savePositionEnhanced(x: Int, y: Int) {
    try {
      val dm = resources.displayMetrics
      var iconW = floatingIconView.width
      var iconH = floatingIconView.height
      if (iconW == 0 || iconH == 0) {
        floatingIconView.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (iconW == 0) iconW = if (floatingIconView.measuredWidth > 0) floatingIconView.measuredWidth else dpToPx(64)
        if (iconH == 0) iconH = if (floatingIconView.measuredHeight > 0) floatingIconView.measuredHeight else dpToPx(64)
      }
      val maxY = (dm.heightPixels - iconH).coerceAtLeast(1)
      val ratio = (y.toFloat() / maxY.toFloat()).coerceIn(0f, 1f)
      val margin = dpToPx(16)
      val leftX = margin
      val rightX = (dm.widthPixels - iconW - margin).coerceAtLeast(0)
      val edge = if (kotlin.math.abs(x - leftX) <= kotlin.math.abs(x - rightX)) "left" else "right"

      getSharedPreferences("persistent_bubble", MODE_PRIVATE)
        .edit()
        .putInt("lastX", x)
        .putInt("lastY", y)
        .putString("lastEdge", edge)
        .putFloat("lastYRatio", ratio)
        .apply()
    } catch (_: Exception) { }
  }

  override fun onConfigurationChanged(newConfig: Configuration) {
    super.onConfigurationChanged(newConfig)
    if (!::floatingIconView.isInitialized || !viewAdded) return
    try {
      var iconW = floatingIconView.width
      var iconH = floatingIconView.height
      if (iconW == 0 || iconH == 0) {
        floatingIconView.measure(
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
          View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (iconW == 0) iconW = if (floatingIconView.measuredWidth > 0) floatingIconView.measuredWidth else dpToPx(64)
        if (iconH == 0) iconH = if (floatingIconView.measuredHeight > 0) floatingIconView.measuredHeight else dpToPx(64)
      }
      val dm = resources.displayMetrics
      val prefs = getSharedPreferences("persistent_bubble", MODE_PRIVATE)
      val ratio = prefs.getFloat("lastYRatio", 0.5f).coerceIn(0f, 1f)
      val edge = prefs.getString("lastEdge", null)
      if (edge != null) {
        val margin = dpToPx(16)
        val maxY = (dm.heightPixels - iconH).coerceAtLeast(0)
        val newY = (ratio * maxY).toInt().coerceIn(0, maxY)
        val newX = if (edge == "left") margin else (dm.widthPixels - iconW - margin).coerceAtLeast(0)
        params.x = newX
        params.y = newY
        windowManager.updateViewLayout(floatingIconView, params)
        savePositionEnhanced(newX, newY)
      }
    } catch (_: Exception) { }
  }
}
