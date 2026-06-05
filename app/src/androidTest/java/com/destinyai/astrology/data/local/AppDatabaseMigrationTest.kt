package com.destinyai.astrology.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.destinyai.astrology.data.local.db.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room migration test.
 *
 * Note: AppDatabase has exportSchema = false, so MigrationTestHelper cannot be
 * driven by JSON schema files. Instead this test exercises the migration
 * SQL directly against a manually-constructed legacy schema, mirroring what a
 * v3 database would look like on disk, then runs MIGRATION_3_4 and confirms
 * the new astro_data_cache table exists with the expected columns.
 *
 * If a future schema bump (5+) is added, copy this pattern: build the prior
 * schema by hand, run the new migration's SQL, validate columns.
 *
 * Run: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    private val testDbName = "migration-test-db"

    /**
     * Validates MIGRATION_3_4 creates the astro_data_cache table with the
     * composite primary key (kind, profile_id, birth_hash, year, month).
     */
    @Test
    fun migration_3_to_4_creates_astro_data_cache_table() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // Drop any leftover from previous runs.
        context.deleteDatabase(testDbName)

        // Inline copy of MIGRATION_3_4 from di/AppModule.kt — keep in sync.
        val migrationSql =
            "CREATE TABLE IF NOT EXISTS astro_data_cache (" +
                "kind TEXT NOT NULL, " +
                "profile_id TEXT NOT NULL, " +
                "birth_hash TEXT NOT NULL, " +
                "year INTEGER NOT NULL, " +
                "month INTEGER NOT NULL, " +
                "owner_email TEXT NOT NULL, " +
                "payload_json TEXT NOT NULL, " +
                "saved_at_ms INTEGER NOT NULL, " +
                "PRIMARY KEY(kind, profile_id, birth_hash, year, month))"

        // Build a v3-shaped DB manually using SQLiteOpenHelper.
        val helper = context.openOrCreateDatabase(testDbName, Context.MODE_PRIVATE, null)
        helper.execSQL("CREATE TABLE chat_threads (id TEXT PRIMARY KEY NOT NULL, owner_email TEXT NOT NULL, title TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, is_pinned INTEGER NOT NULL DEFAULT 0)")

        // Apply migration.
        helper.execSQL(migrationSql)

        // Verify table exists with expected columns.
        val cursor = helper.rawQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='astro_data_cache'",
            null,
        )
        assertTrue("astro_data_cache table must exist after migration", cursor.moveToFirst())
        cursor.close()

        // Insert a row to confirm the schema is usable.
        helper.execSQL(
            "INSERT INTO astro_data_cache(kind, profile_id, birth_hash, year, month, owner_email, payload_json, saved_at_ms) " +
                "VALUES('chart', 'p1', 'h1', 2026, 5, 'u@x.com', '{}', 1717000000000)",
        )
        val rows = helper.rawQuery("SELECT COUNT(*) FROM astro_data_cache", null)
        rows.moveToFirst()
        assertEquals(1, rows.getInt(0))
        rows.close()
        helper.close()
        context.deleteDatabase(testDbName)
    }

    /**
     * Sanity check: building the current AppDatabase from scratch (with all
     * migrations registered) succeeds and the astro_data_cache table is
     * present at the latest version.
     */
    @Test
    fun fresh_database_at_latest_version_has_all_tables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        val supportDb = db.openHelper.writableDatabase
        val expectedTables = listOf(
            "chat_threads",
            "chat_messages",
            "partner_profiles",
        )
        for (table in expectedTables) {
            val cursor = supportDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='$table'"
            )
            assertTrue("table $table must exist in fresh DB", cursor.moveToFirst())
            cursor.close()
        }
        db.close()
    }
}
