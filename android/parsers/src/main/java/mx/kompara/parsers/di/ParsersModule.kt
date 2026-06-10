package mx.kompara.parsers.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import mx.kompara.parsers.NoOpOfferParser
import mx.kompara.parsers.OfferParser

/**
 * Binds the active [OfferParser] implementation. Currently the [NoOpOfferParser]; the
 * spec-driven parsers will replace this binding in a later task.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParsersModule {

    @Binds
    abstract fun bindOfferParser(impl: NoOpOfferParser): OfferParser
}
