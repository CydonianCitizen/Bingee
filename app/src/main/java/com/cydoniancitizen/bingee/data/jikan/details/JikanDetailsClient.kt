package com.cydoniancitizen.bingee.data.jikan.details

import com.cydoniancitizen.bingee.core.model.AnimeDetails
import com.cydoniancitizen.bingee.core.model.ExternalMediaRef
import com.cydoniancitizen.bingee.core.model.MediaSource
import com.cydoniancitizen.bingee.core.result.AppError
import com.cydoniancitizen.bingee.core.result.AppResult
import com.cydoniancitizen.bingee.data.jikan.JikanRequestGate
import com.cydoniancitizen.bingee.data.jikan.jikanCall
import com.cydoniancitizen.bingee.data.jikan.search.JikanSearchService
import javax.inject.Inject

internal class JikanDetailsClient @Inject constructor(
    private val service: JikanSearchService,
    private val gate: JikanRequestGate
) {
    suspend fun load(reference: ExternalMediaRef): AppResult<AnimeDetails> {
        val id = reference.externalId.toIntOrNull()?.takeIf { it > 0 }
        if (reference.source != MediaSource.JIKAN || id == null) return AppResult.Failure(AppError.InvalidInput)
        return jikanCall(gate, { service.getAnimeFull(id) }) { JikanDetailsMapper.map(it) }
            .let { result ->
                if (result is AppResult.Success && result.value.externalRef != reference) {
                    AppResult.Failure(AppError.InvalidRemoteResponse)
                } else {
                    result
                }
            }
    }
}
