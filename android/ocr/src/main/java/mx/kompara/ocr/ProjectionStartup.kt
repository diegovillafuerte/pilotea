package mx.kompara.ocr

internal enum class ProjectionStartupState { NEW, PREPARING, DISPLAY_READY, WIRED, AVAILABLE, FAILED }

/** A projection is not an owner until its display, image listener and consumer are ready. */
internal class ProjectionStartup {
    var state = ProjectionStartupState.NEW
        private set

    fun start(
        prepare: () -> Unit,
        createDisplay: () -> Any?,
        wireReader: () -> Unit,
        register: () -> Unit,
        failure: (Throwable) -> Unit,
    ): Boolean {
        check(state == ProjectionStartupState.NEW)
        return try {
            state = ProjectionStartupState.PREPARING
            prepare()
            checkNotNull(createDisplay())
            state = ProjectionStartupState.DISPLAY_READY
            wireReader()
            state = ProjectionStartupState.WIRED
            register()
            state = ProjectionStartupState.AVAILABLE
            true
        } catch (error: Throwable) {
            state = ProjectionStartupState.FAILED
            failure(error)
            false
        }
    }
}
