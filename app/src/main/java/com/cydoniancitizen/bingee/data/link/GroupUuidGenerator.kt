package com.cydoniancitizen.bingee.data.link

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal fun interface GroupUuidGenerator {
    fun generateGroupUuid(): String
}

@Singleton
internal class RandomGroupUuidGenerator @Inject constructor() : GroupUuidGenerator {
    override fun generateGroupUuid(): String = UUID.randomUUID().toString()
}
