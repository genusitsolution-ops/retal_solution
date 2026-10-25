package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.NewQuery
import com.genusit.prms.domain.model.Query
import com.genusit.prms.domain.model.QueryDetail
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.repository.QueryRepository
import javax.inject.Inject

class GetOwnerQueriesUseCase @Inject constructor(private val repo: QueryRepository) {
    suspend operator fun invoke(): List<Query> = repo.ownerQueries()
}

class GetTenantQueriesUseCase @Inject constructor(private val repo: QueryRepository) {
    suspend operator fun invoke(): List<Query> = repo.tenantQueries()
}

/** Ticket conversation for the owner or the tenant (each has its own endpoint). */
class GetQueryDetailUseCase @Inject constructor(private val repo: QueryRepository) {
    suspend operator fun invoke(role: Role, id: Int): QueryDetail =
        if (role == Role.OWNER) repo.ownerQuery(id) else repo.tenantQuery(id)
}

/**
 * Sends a reply and/or (owner only) a status change, exactly as the screen allows:
 * a message is sent with the optional new status; without a message only the status changes.
 * Returns false when there was nothing to send.
 */
class ReplyToQueryUseCase @Inject constructor(private val repo: QueryRepository) {
    suspend operator fun invoke(role: Role, id: Int, message: String, newStatus: String?): Boolean {
        val text = message.trim()
        when {
            text.isNotEmpty() && role == Role.OWNER -> repo.ownerReply(id, text, newStatus)
            text.isNotEmpty() -> repo.tenantReply(id, text)
            newStatus != null && role == Role.OWNER -> repo.ownerSetStatus(id, newStatus)
            else -> return false
        }
        return true
    }
}

class RaiseQueryUseCase @Inject constructor(private val repo: QueryRepository) {
    suspend operator fun invoke(q: NewQuery): CreatedId = repo.raise(q)
}
