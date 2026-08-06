package com.cydoniancitizen.bingee.core.model

import java.time.Instant

@JvmInline
value class MediaLinkGroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "MediaLinkGroupId value must not be blank" }
    }
}

data class LinkedMediaIdentity(val source: MediaSource, val mediaType: MediaType, val externalId: String) {
    init {
        require(externalId.isNotBlank()) { "External ID must not be blank" }
    }
}

data class MediaLinkGroup(
    val groupId: MediaLinkGroupId,
    val first: LinkedMediaIdentity,
    val second: LinkedMediaIdentity,
    val preferredPresentation: LinkedMediaIdentity,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(first != second) { "Linked identities must be distinct" }
        require(preferredPresentation == first || preferredPresentation == second) {
            "Preferred presentation must be one of the linked identities"
        }
    }
}

enum class MediaLinkAuditAction {
    LINKED,
    UNLINKED,
    PREFERRED_PRESENTATION_CHANGED
}

enum class MediaLinkAuditOrigin {
    MANUAL_USER_ACTION,
    RESTORED_BACKUP
}

data class MediaLinkAuditEvent(
    val auditId: Long = 0,
    val groupId: MediaLinkGroupId,
    val action: MediaLinkAuditAction,
    val timestamp: Instant,
    val origin: MediaLinkAuditOrigin,
    val memberSnapshot: List<LinkedMediaIdentity>,
    val preferredPresentationSnapshot: LinkedMediaIdentity? = null
)
