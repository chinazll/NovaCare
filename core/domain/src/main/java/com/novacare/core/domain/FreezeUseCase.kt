package com.novacare.core.domain

import com.novacare.core.ai.FreezeAdvisor
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeResult
import com.novacare.core.system.FreezeController
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreezeUseCase @Inject constructor(
    private val controller: FreezeController,
) {

    fun candidates(snapshot: DeviceSnapshot, includeSystem: Boolean): List<FreezeCandidate> =
        FreezeAdvisor.advise(snapshot.apps, snapshot.usage, snapshot.nowMs, includeSystem)

    suspend fun freeze(packageName: String, advancedMode: Boolean): FreezeResult =
        controller.freeze(packageName, preferShizuku = advancedMode)

    suspend fun unfreeze(packageName: String): FreezeResult =
        controller.unfreeze(packageName)
}
