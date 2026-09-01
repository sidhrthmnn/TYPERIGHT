package com.aistudio.typeright.domain.usecase

import com.aistudio.typeright.domain.model.Result
import com.aistudio.typeright.domain.repository.ClipboardRepository
import com.aistudio.typeright.domain.repository.ClipboardItem
import javax.inject.Inject
import java.util.UUID

/**
 * Use case for managing clipboard history
 */
class GetClipboardHistoryUseCase @Inject constructor(
    private val clipboardRepository: ClipboardRepository
) {
    suspend operator fun invoke(limit: Int = 50): Result<List<ClipboardItem>> {
        return clipboardRepository.getClipboardHistory(limit)
    }
}

/**
 * Use case for pinning clipboard items
 */
class PinClipboardItemUseCase @Inject constructor(
    private val clipboardRepository: ClipboardRepository
) {
    suspend operator fun invoke(text: String): Result<Unit> {
        val item = ClipboardItem(
            id = UUID.randomUUID().toString(),
            text = text,
            isPinned = true
        )
        return clipboardRepository.pinItem(item)
    }
}
