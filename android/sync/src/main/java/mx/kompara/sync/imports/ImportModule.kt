package mx.kompara.sync.imports

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Binds the B-045 import seam ([Importer]) to its live implementation ([ImportRepository]). The
 * import flow's ViewModel (in `:ui`) injects [Importer] so it stays unit-testable with a plain fake;
 * Hilt resolves it to the real repository here.
 */
@Module
@InstallIn(SingletonComponent::class)
object ImportModule {

    @Provides
    @Singleton
    fun provideImporter(repository: ImportRepository): Importer = repository
}
