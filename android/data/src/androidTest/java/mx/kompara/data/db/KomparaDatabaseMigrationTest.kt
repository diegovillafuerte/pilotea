package mx.kompara.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * Instrumented migration test harness for [KomparaDatabase].
 *
 * Runs on a device/emulator (out of scope for CI unit tests). It loads the v1 schema exported to
 * `data/schemas/` and verifies the database opens — the seam where future `Migration` objects get
 * asserted as new versions land. Add `helper.runMigrationsAndValidate(...)` calls per migration.
 */
class KomparaDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KomparaDatabase::class.java,
    )

    @Test
    @Throws(IOException::class)
    fun migrate_createsVersion1Schema() {
        helper.createDatabase(TEST_DB, 1).close()
        // When v2 lands: helper.runMigrationsAndValidate(TEST_DB, 2, true, MIGRATION_1_2)
    }

    companion object {
        private const val TEST_DB = "kompara-migration-test"
    }
}
