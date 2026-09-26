package com.prabhupadaconnect.vedabase.data.user

import java.time.Instant

/**
 * Pure Last-Write-Wins conflict resolution, factored out of
 * [UserRepository.mergeSyncChanges] so the decision logic itself - the part
 * that actually has to be correct for multi-device sync to never silently
 * lose or duplicate data - can be unit-tested without a database.
 *
 * Ported 1:1 from the desktop app's `SqliteUserRepository.MergeSyncChangesAsync`
 * (C#): given a remote (incoming) and local (existing) row for the same
 * entity id, the remote side wins if its `updatedUtc` is strictly newer; the
 * local side wins (and the merge counts it as a conflict) if the remote is
 * strictly older; and on an exact tie, the row whose id sorts first
 * ordinally against the local device id wins - deterministic, never a coin
 * flip, so two devices reprocessing the same pulled batch always agree.
 */
object LwwMerge {

    data class Decision(val remoteWins: Boolean, val isConflict: Boolean)

    fun resolve(
        remoteUpdatedUtc: Instant,
        localUpdatedUtc: Instant,
        remoteId: String,
        localDeviceId: String
    ): Decision = when {
        remoteUpdatedUtc > localUpdatedUtc -> Decision(remoteWins = true, isConflict = false)
        remoteUpdatedUtc < localUpdatedUtc -> Decision(remoteWins = false, isConflict = true)
        else -> Decision(remoteWins = remoteId < localDeviceId, isConflict = false)
    }

    /**
     * The bookmark-specific "at most one active bookmark per verse" guard,
     * used when an incoming *new* (never-seen-id) active bookmark collides
     * with a sibling row already active on the same RecordKey. Ties go to
     * the incoming row (`>=`), matching the desktop app's insert-path
     * behavior exactly - this is a separate, narrower rule than [resolve]
     * because there is no existing row with the SAME id to compare against,
     * only a different id claiming the same verse.
     */
    fun incomingBookmarkWinsUniqueness(incomingUpdatedUtc: Instant, activeSiblingUpdatedUtc: Instant): Boolean =
        incomingUpdatedUtc >= activeSiblingUpdatedUtc
}
