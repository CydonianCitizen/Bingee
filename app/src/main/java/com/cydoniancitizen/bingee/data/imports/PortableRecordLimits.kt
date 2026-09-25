package com.cydoniancitizen.bingee.data.imports

/** Shared record-count ceilings for portable backup data and source imports. */
internal object PortableRecordLimits {
    const val MAX_MEDIA = 50_000
    const val MAX_SEASONS = 100_000
    const val MAX_EPISODES = 100_000
}
