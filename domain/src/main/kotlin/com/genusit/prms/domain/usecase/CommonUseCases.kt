package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.Meta
import com.genusit.prms.domain.model.NotificationList
import com.genusit.prms.domain.model.Receipt
import com.genusit.prms.domain.repository.CommonRepository
import javax.inject.Inject

class GetMetaUseCase @Inject constructor(private val repo: CommonRepository) {
    suspend operator fun invoke(): Meta = repo.meta()
}

class GetNotificationsUseCase @Inject constructor(private val repo: CommonRepository) {
    suspend operator fun invoke(): NotificationList = repo.notifications()
}

class MarkAllNotificationsReadUseCase @Inject constructor(private val repo: CommonRepository) {
    suspend operator fun invoke() = repo.markAllNotificationsRead()
}

class GetReceiptUseCase @Inject constructor(private val repo: CommonRepository) {
    suspend operator fun invoke(receiptNumber: String): Receipt = repo.receipt(receiptNumber)
}
