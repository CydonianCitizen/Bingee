package com.cydoniancitizen.bingee.core.common

import javax.inject.Inject
import javax.inject.Singleton

interface AnimeFeatureAvailability {
    val isAvailable: Boolean
}

@Singleton
class ProductionAnimeFeatureAvailability @Inject constructor() : AnimeFeatureAvailability {
    override val isAvailable: Boolean = false
}

class TestingAnimeFeatureAvailability(override val isAvailable: Boolean = true) : AnimeFeatureAvailability
