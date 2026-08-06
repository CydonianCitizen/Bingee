package com.cydoniancitizen.bingee.domain.repository

import com.cydoniancitizen.bingee.core.model.LinkedMediaIdentity
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceCandidate
import com.cydoniancitizen.bingee.domain.equivalence.MediaEquivalenceEvaluation
import kotlinx.coroutines.flow.Flow

interface MediaEquivalenceCandidateRepository {
    fun observeLibraryCandidates(): Flow<List<MediaEquivalenceCandidate>>

    fun observeCandidatesForMedia(identity: LinkedMediaIdentity): Flow<List<MediaEquivalenceCandidate>>

    suspend fun evaluatePair(
        first: LinkedMediaIdentity,
        second: LinkedMediaIdentity
    ): AppResult<MediaEquivalenceEvaluation>
}
