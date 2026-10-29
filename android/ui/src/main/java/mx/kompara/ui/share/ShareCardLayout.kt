package mx.kompara.ui.share

/** A single 4:5 artefact. The layout can grow for long dates/copy without truncating a glyph. */
object ShareCardLayout {
    const val WIDTH = 1080
    const val HEIGHT = 1350
    const val INSET = 16f
    const val RADIUS = 16f
    fun height(width: Float) = width * 5f / 4f
    fun factTop(height: Float, rows: Int) = height - INSET - rows * 32f
}
