package com.cydoniancitizen.bingee.data.notification

import com.cydoniancitizen.bingee.core.model.NotificationDeliveryIdentity
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal fun deterministicNotificationId(identity: NotificationDeliveryIdentity): Int {
    val canonical = listOf(
        identity.source.name,
        identity.subjectType.name,
        identity.subjectExternalId,
        identity.eventType.name,
        identity.eventDate.toString(),
        identity.leadDays.toString()
    ).joinToString(separator = "\u001f")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(StandardCharsets.UTF_8))
    return ByteBuffer.wrap(digest).int and Int.MAX_VALUE
}
