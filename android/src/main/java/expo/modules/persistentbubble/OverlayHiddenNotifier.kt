package expo.modules.persistentbubble

object OverlayHiddenNotifier {
  @Volatile
  private var listener: ((Boolean) -> Unit)? = null

  fun setListener(l: ((Boolean) -> Unit)?) {
    listener = l
  }

  fun notifyHidden(hidden: Boolean) {
    listener?.invoke(hidden)
  }
}
