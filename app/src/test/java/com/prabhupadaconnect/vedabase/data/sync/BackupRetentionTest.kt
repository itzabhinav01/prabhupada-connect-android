package com.prabhupadaconnect.vedabase.data.sync

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRetentionTest {

    private fun snapshot(name: String) = File("/backups/$name")

    @Test
    fun `fewer snapshots than the keep count deletes nothing`() {
        val snapshots = listOf(
            snapshot("user_20260101_000000.db.bak"),
            snapshot("user_20260102_000000.db.bak")
        )
        assertTrue(BackupRetention.filesToDelete(snapshots, keep = 5).isEmpty())
    }

    @Test
    fun `exactly the keep count deletes nothing`() {
        val snapshots = (1..5).map { snapshot("user_2026010${it}_000000.db.bak") }
        assertTrue(BackupRetention.filesToDelete(snapshots, keep = 5).isEmpty())
    }

    @Test
    fun `more than the keep count deletes only the oldest excess`() {
        // Zero-padded yyyyMMdd_HHmmss names sort lexicographically = chronologically.
        val snapshots = listOf(
            snapshot("user_20260101_000000.db.bak"), // oldest
            snapshot("user_20260102_000000.db.bak"),
            snapshot("user_20260103_000000.db.bak"),
            snapshot("user_20260104_000000.db.bak"),
            snapshot("user_20260105_000000.db.bak"),
            snapshot("user_20260106_000000.db.bak"),
            snapshot("user_20260107_000000.db.bak")  // newest
        )
        val toDelete = BackupRetention.filesToDelete(snapshots, keep = 5)

        assertEquals(2, toDelete.size)
        assertTrue(toDelete.any { it.name == "user_20260101_000000.db.bak" })
        assertTrue(toDelete.any { it.name == "user_20260102_000000.db.bak" })
        // None of the 5 newest are ever marked for deletion.
        assertTrue(toDelete.none { it.name == "user_20260103_000000.db.bak" })
        assertTrue(toDelete.none { it.name == "user_20260107_000000.db.bak" })
    }

    @Test
    fun `input order does not affect which files are kept`() {
        val snapshots = listOf(
            snapshot("user_20260107_000000.db.bak"),
            snapshot("user_20260101_000000.db.bak"),
            snapshot("user_20260105_000000.db.bak"),
            snapshot("user_20260103_000000.db.bak"),
            snapshot("user_20260104_000000.db.bak"),
            snapshot("user_20260102_000000.db.bak"),
            snapshot("user_20260106_000000.db.bak")
        )
        val toDelete = BackupRetention.filesToDelete(snapshots, keep = 5).map { it.name }.toSet()
        assertEquals(setOf("user_20260101_000000.db.bak", "user_20260102_000000.db.bak"), toDelete)
    }

    @Test
    fun `empty snapshot list deletes nothing`() {
        assertTrue(BackupRetention.filesToDelete(emptyList(), keep = 5).isEmpty())
    }

    @Test
    fun `keep of zero deletes every snapshot`() {
        val snapshots = listOf(snapshot("user_20260101_000000.db.bak"), snapshot("user_20260102_000000.db.bak"))
        assertEquals(2, BackupRetention.filesToDelete(snapshots, keep = 0).size)
    }
}
