package expo.modules.persistentbubble

object ActiveChangeNotifier {
  @Volatile
  private var listener: ((Boolean) -> Unit)? = null

  fun setListener(l: ((Boolean) -> Unit)?) {
    listener = l
  }

  fun notify(isActive: Boolean) {
    listener?.invoke(isActive)
  }
}
