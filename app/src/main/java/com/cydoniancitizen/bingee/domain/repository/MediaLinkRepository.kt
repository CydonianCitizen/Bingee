package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.result.AppResult
import kotlinx.coroutines.flow.Flow

interface MediaLinkRepository {
    fun observeLinkForMedia(identity: LinkedMediaIdentity): Flow<MediaLinkGroup?>

    fun observeLinkGroup(groupId: MediaLinkGroupId): Flow<MediaLinkGroup?>

    suspend fun createLink(
        first: LinkedMediaIdentity,
        second: LinkedMediaIdentity,
        preferredPresentation: LinkedMediaIdentity,
        origin: MediaLinkAuditOrigin = MediaLinkAuditOrigin.MANUAL_USER_ACTION
    ): AppResult<MediaLinkGroup>

    suspend fun changePreferredPresentation(
        groupId: MediaLinkGroupId,
        preferredPresentation: LinkedMediaIdentity,
        origin: MediaLinkAuditOrigin = MediaLinkAuditOrigin.MANUAL_USER_ACTION
    ): AppResult<MediaLinkGroup>

    suspend fun unlink(
        groupId: MediaLinkGroupId,
        origin: MediaLinkAuditOrigin = MediaLinkAuditOrigin.MANUAL_USER_ACTION
    ): AppResult<Unit>
}
