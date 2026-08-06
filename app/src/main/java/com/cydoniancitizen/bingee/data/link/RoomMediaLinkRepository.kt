package com.cydoniancitizen.bingee.data.link

import androidx.room.withTransaction
import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditAction
import com.cydoniancitizen.bingee.core.model.MediaLinkAuditOrigin
import com.cydoniancitizen.bingee.core.model.MediaLinkGroup
import com.cydoniancitizen.bingee.core.model.MediaLinkGroupId
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.library.local.BingeeDatabase
import com.cydoniancitizen.bingee.data.library.local.MediaLinkAuditEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkAuditMemberEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkDao
import com.cydoniancitizen.bingee.data.library.local.MediaLinkGroupEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkMemberEntity
import com.cydoniancitizen.bingee.data.library.local.MediaLinkMemberWithIdentity
import com.cydoniancitizen.bingee.domain.repository.MediaLinkRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

internal enum class LinkTransactionStage {
    BEFORE_GROUP_INSERT,
    AFTER_GROUP_INSERT,
    AFTER_MEMBERS_INSERT,
    AFTER_AUDIT_INSERT,
    AFTER_AUDIT_MEMBERS_INSERT,
    BEFORE_PREFERRED_UPDATE,
    AFTER_PREFERRED_UPDATE,
    AFTER_PREFERRED_AUDIT_INSERT,
    BEFORE_UNLINK_DELETE,
    AFTER_UNLINK_AUDIT_INSERT
}

internal fun interface LinkTransactionFailureInjector {
    fun check(stage: LinkTransactionStage)
}

@Singleton
internal class RoomMediaLinkRepository @Inject constructor(
    private val database: BingeeDatabase,
    private val mediaLinkDao: MediaLinkDao,
    private val clock: Clock,
    private val uuidGenerator: GroupUuidGenerator
) : MediaLinkRepository {

    var failureInjector: LinkTransactionFailureInjector = LinkTransactionFailureInjector {}

    override fun observeLinkForMedia(identity: LinkedMediaIdentity): Flow<MediaLinkGroup?> {
        val groupFlow = mediaLinkDao.observeGroupEntityByMediaIdentity(
            identity.source,
            identity.mediaType,
            identity.externalId
        )
        val membersFlow = mediaLinkDao.observeMembersWithIdentityByMediaIdentity(
            identity.source,
            identity.mediaType,
            identity.externalId
        )
        return combine(groupFlow, membersFlow) { groupEntity, members ->
            if (groupEntity == null || members.size != 2) {
                null
            } else {
                mapToDomainGroup(groupEntity, members)
            }
        }
    }

    override fun observeLinkGroup(groupId: MediaLinkGroupId): Flow<MediaLinkGroup?> {
        val groupFlow = mediaLinkDao.observeGroupEntityByUuid(groupId.value)
        val membersFlow = mediaLinkDao.observeMembersWithIdentityByGroupUuid(groupId.value)
        return combine(groupFlow, membersFlow) { groupEntity, members ->
            if (groupEntity == null || members.size != 2) {
                null
            } else {
                mapToDomainGroup(groupEntity, members)
            }
        }
    }

    override suspend fun createLink(
        first: LinkedMediaIdentity,
        second: LinkedMediaIdentity,
        preferredPresentation: LinkedMediaIdentity,
        origin: MediaLinkAuditOrigin
    ): AppResult<MediaLinkGroup> {
        if (first == second) {
            return AppResult.Failure(AppError.LinkError.SelfLinkProhibited)
        }
        if (preferredPresentation != first && preferredPresentation != second) {
            return AppResult.Failure(AppError.LinkError.InvalidPreferredMember)
        }

        return try {
            database.withTransaction {
                val firstMediaId = mediaLinkDao.findMediaIdByIdentity(
                    first.source,
                    first.mediaType,
                    first.externalId
                ) ?: return@withTransaction AppResult.Failure(AppError.LinkError.MediaNotFound)

                val secondMediaId = mediaLinkDao.findMediaIdByIdentity(
                    second.source,
                    second.mediaType,
                    second.externalId
                ) ?: return@withTransaction AppResult.Failure(AppError.LinkError.MediaNotFound)

                if (firstMediaId == secondMediaId) {
                    return@withTransaction AppResult.Failure(AppError.LinkError.SelfLinkProhibited)
                }

                if (mediaLinkDao.findGroupEntityByMediaId(firstMediaId) != null ||
                    mediaLinkDao.findGroupEntityByMediaId(secondMediaId) != null
                ) {
                    return@withTransaction AppResult.Failure(AppError.LinkError.AlreadyLinked)
                }

                val preferredMediaId = if (preferredPresentation == first) firstMediaId else secondMediaId
                val now = clock.instant()
                val groupUuid = uuidGenerator.generateGroupUuid()

                if (mediaLinkDao.findGroupEntityByUuid(groupUuid) != null) {
                    return@withTransaction AppResult.Failure(AppError.LinkError.LinkConflict)
                }

                failureInjector.check(LinkTransactionStage.BEFORE_GROUP_INSERT)

                val localGroupId = mediaLinkDao.insertGroup(
                    MediaLinkGroupEntity(
                        groupUuid = groupUuid,
                        preferredPresentationMediaId = preferredMediaId,
                        createdAt = now,
                        updatedAt = now
                    )
                )

                failureInjector.check(LinkTransactionStage.AFTER_GROUP_INSERT)

                mediaLinkDao.insertMembers(
                    listOf(
                        MediaLinkMemberEntity(localGroupId, firstMediaId, now),
                        MediaLinkMemberEntity(localGroupId, secondMediaId, now)
                    )
                )

                failureInjector.check(LinkTransactionStage.AFTER_MEMBERS_INSERT)

                val auditId = mediaLinkDao.insertAudit(
                    MediaLinkAuditEntity(
                        groupUuid = groupUuid,
                        action = MediaLinkAuditAction.LINKED,
                        actionTimestamp = now,
                        origin = origin,
                        preferredSource = preferredPresentation.source,
                        preferredMediaType = preferredPresentation.mediaType,
                        preferredExternalId = preferredPresentation.externalId
                    )
                )

                failureInjector.check(LinkTransactionStage.AFTER_AUDIT_INSERT)

                mediaLinkDao.insertAuditMembers(
                    listOf(
                        MediaLinkAuditMemberEntity(auditId, first.source, first.mediaType, first.externalId),
                        MediaLinkAuditMemberEntity(auditId, second.source, second.mediaType, second.externalId)
                    )
                )

                failureInjector.check(LinkTransactionStage.AFTER_AUDIT_MEMBERS_INSERT)

                AppResult.Success(
                    MediaLinkGroup(
                        groupId = MediaLinkGroupId(groupUuid),
                        first = first,
                        second = second,
                        preferredPresentation = preferredPresentation,
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message?.startsWith("TEST_INJECTED_FAILURE") == true) {
                throw e
            }
            AppResult.Failure(AppError.LocalStorageFailure)
        }
    }

    override suspend fun changePreferredPresentation(
        groupId: MediaLinkGroupId,
        preferredPresentation: LinkedMediaIdentity,
        origin: MediaLinkAuditOrigin
    ): AppResult<MediaLinkGroup> {
        return try {
            database.withTransaction {
                val groupEntity = mediaLinkDao.findGroupEntityByUuid(groupId.value)
                    ?: return@withTransaction AppResult.Failure(AppError.LinkError.LinkGroupNotFound)

                val members = mediaLinkDao.findMembersWithIdentityByGroupId(groupEntity.localGroupId)
                if (members.size != 2) {
                    return@withTransaction AppResult.Failure(AppError.LinkError.CorruptedGroup)
                }

                val preferredMember = members.find {
                    it.source == preferredPresentation.source &&
                        it.mediaType == preferredPresentation.mediaType &&
                        it.externalId == preferredPresentation.externalId
                } ?: return@withTransaction AppResult.Failure(AppError.LinkError.InvalidPreferredMember)

                val firstIdentity = LinkedMediaIdentity(members[0].source, members[0].mediaType, members[0].externalId)
                val secondIdentity = LinkedMediaIdentity(members[1].source, members[1].mediaType, members[1].externalId)

                if (groupEntity.preferredPresentationMediaId == preferredMember.localMediaId) {
                    return@withTransaction AppResult.Success(
                        MediaLinkGroup(
                            groupId = groupId,
                            first = firstIdentity,
                            second = secondIdentity,
                            preferredPresentation = preferredPresentation,
                            createdAt = groupEntity.createdAt,
                            updatedAt = groupEntity.updatedAt
                        )
                    )
                }

                failureInjector.check(LinkTransactionStage.BEFORE_PREFERRED_UPDATE)

                val now = clock.instant()
                mediaLinkDao.updatePreferredPresentation(groupEntity.localGroupId, preferredMember.localMediaId, now)

                failureInjector.check(LinkTransactionStage.AFTER_PREFERRED_UPDATE)

                val auditId = mediaLinkDao.insertAudit(
                    MediaLinkAuditEntity(
                        groupUuid = groupId.value,
                        action = MediaLinkAuditAction.PREFERRED_PRESENTATION_CHANGED,
                        actionTimestamp = now,
                        origin = origin,
                        preferredSource = preferredPresentation.source,
                        preferredMediaType = preferredPresentation.mediaType,
                        preferredExternalId = preferredPresentation.externalId
                    )
                )

                mediaLinkDao.insertAuditMembers(
                    listOf(
                        MediaLinkAuditMemberEntity(
                            auditId,
                            firstIdentity.source,
                            firstIdentity.mediaType,
                            firstIdentity.externalId
                        ),
                        MediaLinkAuditMemberEntity(
                            auditId,
                            secondIdentity.source,
                            secondIdentity.mediaType,
                            secondIdentity.externalId
                        )
                    )
                )

                failureInjector.check(LinkTransactionStage.AFTER_PREFERRED_AUDIT_INSERT)

                AppResult.Success(
                    MediaLinkGroup(
                        groupId = groupId,
                        first = firstIdentity,
                        second = secondIdentity,
                        preferredPresentation = preferredPresentation,
                        createdAt = groupEntity.createdAt,
                        updatedAt = now
                    )
                )
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message?.startsWith("TEST_INJECTED_FAILURE") == true) {
                throw e
            }
            AppResult.Failure(AppError.LocalStorageFailure)
        }
    }

    override suspend fun unlink(groupId: MediaLinkGroupId, origin: MediaLinkAuditOrigin): AppResult<Unit> {
        return try {
            database.withTransaction {
                val groupEntity = mediaLinkDao.findGroupEntityByUuid(groupId.value)
                    ?: return@withTransaction AppResult.Failure(AppError.LinkError.LinkGroupNotFound)

                val members = mediaLinkDao.findMembersWithIdentityByGroupId(groupEntity.localGroupId)
                val now = clock.instant()

                failureInjector.check(LinkTransactionStage.BEFORE_UNLINK_DELETE)

                if (members.isNotEmpty()) {
                    val auditId = mediaLinkDao.insertAudit(
                        MediaLinkAuditEntity(
                            groupUuid = groupId.value,
                            action = MediaLinkAuditAction.UNLINKED,
                            actionTimestamp = now,
                            origin = origin
                        )
                    )
                    mediaLinkDao.insertAuditMembers(
                        members.map {
                            MediaLinkAuditMemberEntity(auditId, it.source, it.mediaType, it.externalId)
                        }
                    )
                }

                failureInjector.check(LinkTransactionStage.AFTER_UNLINK_AUDIT_INSERT)

                mediaLinkDao.deleteGroup(groupEntity.localGroupId)

                AppResult.Success(Unit)
            }
        } catch (e: Exception) {
            if (e is IllegalStateException && e.message?.startsWith("TEST_INJECTED_FAILURE") == true) {
                throw e
            }
            AppResult.Failure(AppError.LocalStorageFailure)
        }
    }

    private fun mapToDomainGroup(
        groupEntity: MediaLinkGroupEntity,
        members: List<MediaLinkMemberWithIdentity>
    ): MediaLinkGroup? {
        if (members.size != 2) return null
        val first = LinkedMediaIdentity(members[0].source, members[0].mediaType, members[0].externalId)
        val second = LinkedMediaIdentity(members[1].source, members[1].mediaType, members[1].externalId)
        val preferredMember = members.find { it.localMediaId == groupEntity.preferredPresentationMediaId }
            ?: return null
        val preferred =
            LinkedMediaIdentity(preferredMember.source, preferredMember.mediaType, preferredMember.externalId)
        return MediaLinkGroup(
            groupId = MediaLinkGroupId(groupEntity.groupUuid),
            first = first,
            second = second,
            preferredPresentation = preferred,
            createdAt = groupEntity.createdAt,
            updatedAt = groupEntity.updatedAt
        )
    }
}
