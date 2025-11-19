package expo.modules.persistentbubble

object IconRemovedNotifier {
  @Volatile
  private var listener: (() -> Unit)? = null

  fun setListener(l: (() -> Unit)?) {
    listener = l
  }

  fun notifyRemoved() {
    listener?.invoke()
  }
}
