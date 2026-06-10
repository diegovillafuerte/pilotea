package mx.kompara.data.di

import javax.inject.Qualifier

/**
 * Qualifiers for the injected [kotlinx.coroutines.CoroutineDispatcher]s.
 *
 * Injecting dispatchers (rather than referencing [kotlinx.coroutines.Dispatchers] directly)
 * keeps coroutine code testable: tests can substitute a test dispatcher via Hilt overrides.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MainDispatcher
