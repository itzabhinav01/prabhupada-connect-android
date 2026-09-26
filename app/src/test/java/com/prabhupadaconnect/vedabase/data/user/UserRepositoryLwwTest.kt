package com.prabhupadaconnect.vedabase.data.user

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure LWW decision logic in [LwwMerge], extracted from
 * `UserRepository.mergeSyncChanges` so multi-device conflict resolution can
 * be verified without Room or a real sync round-trip.
 */
class UserRepositoryLwwTest {

    private val t0 = Instant.parse("2026-01-01T00:00:00Z")
    private val t1 = Instant.parse("2026-01-01T00:00:01Z")

    @Test
    fun `strictly newer remote wins and is not a conflict`() {
        val decision = LwwMerge.resolve(
            remoteUpdatedUtc = t1,
            localUpdatedUtc = t0,
            remoteId = "remote-device",
            localDeviceId = "local-device"
        )
        assertTrue(decision.remoteWins)
        assertFalse(decision.isConflict)
    }

    @Test
    fun `strictly older remote loses and is flagged as a conflict`() {
        val decision = LwwMerge.resolve(
            remoteUpdatedUtc = t0,
            localUpdatedUtc = t1,
            remoteId = "remote-device",
            localDeviceId = "local-device"
        )
        assertFalse(decision.remoteWins)
        assertTrue(decision.isConflict)
    }

    @Test
    fun `exact timestamp tie is broken by ordinal id comparison, not a conflict`() {
        val remoteWins = LwwMerge.resolve(
            remoteUpdatedUtc = t0,
            localUpdatedUtc = t0,
            remoteId = "aaa-remote",
            localDeviceId = "zzz-local"
        )
        assertTrue(remoteWins.remoteWins) // "aaa-remote" < "zzz-local" ordinally
        assertFalse(remoteWins.isConflict)

        val localWins = LwwMerge.resolve(
            remoteUpdatedUtc = t0,
            localUpdatedUtc = t0,
            remoteId = "zzz-remote",
            localDeviceId = "aaa-local"
        )
        assertFalse(localWins.remoteWins) // "zzz-remote" > "aaa-local" ordinally
        assertFalse(localWins.isConflict)
    }

    @Test
    fun `tie break is deterministic across repeated calls`() {
        val first = LwwMerge.resolve(t0, t0, "device-a", "device-b")
        val second = LwwMerge.resolve(t0, t0, "device-a", "device-b")
        assertTrue(first.remoteWins == second.remoteWins)
    }

    @Test
    fun `identical ids at a tie always resolve the same way regardless of call order`() {
        val decision = LwwMerge.resolve(t0, t0, "same-id", "same-id")
        // "same-id" < "same-id" is false, so the local side wins the tie.
        assertFalse(decision.remoteWins)
        assertFalse(decision.isConflict)
    }

    // -------------------- incomingBookmarkWinsUniqueness --------------------

    @Test
    fun `incoming bookmark strictly newer than the active sibling wins`() {
        assertTrue(LwwMerge.incomingBookmarkWinsUniqueness(t1, t0))
    }

    @Test
    fun `incoming bookmark strictly older than the active sibling loses`() {
        assertFalse(LwwMerge.incomingBookmarkWinsUniqueness(t0, t1))
    }

    @Test
    fun `incoming bookmark tied with the active sibling wins ties`() {
        assertTrue(LwwMerge.incomingBookmarkWinsUniqueness(t0, t0))
    }
}
