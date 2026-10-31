package com.genusit.prms.domain.repository

import com.genusit.prms.domain.model.AgentHandover
import com.genusit.prms.domain.model.CollectionDashboard
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.EmployeeCollectionDetail
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.SettlementDetail
import com.genusit.prms.domain.model.SettlementPage

/** Owner: collection dashboard, employee-wise figures and review of cash handovers. */
interface OwnerSettlementRepository {
    suspend fun dashboard(filter: CollectionFilter): CollectionDashboard
    suspend fun employee(employeeId: Int, from: String, to: String, propertyId: Int, page: Int): EmployeeCollectionDetail
    suspend fun settlements(status: String, employeeId: Int, page: Int): SettlementPage
    suspend fun detail(id: Int): SettlementDetail
    suspend fun confirm(id: Int, amountReceived: Double, note: String): SettlementDetail
    suspend fun reject(id: Int, reason: String): SettlementDetail
    /** The owner records money received directly from an employee (confirmed at once). */
    suspend fun record(employeeId: Int, input: HandoverInput, idempotencyKey: String): SettlementDetail
}

/** Employee (collection agent): own balance and handovers only. */
interface AgentSettlementRepository {
    suspend fun balance(): AgentHandover
    suspend fun settlements(status: String, page: Int): SettlementPage
    suspend fun detail(id: Int): SettlementDetail
    suspend fun submit(input: HandoverInput, idempotencyKey: String): SettlementDetail
    suspend fun cancel(id: Int): SettlementDetail
}

/** Profile photos (owner and tenant). [id] null = the signed-in user. */
interface PhotoRepository {
    /** Returns the new photo version. A tenant photo taken by the owner needs [consent]. */
    suspend fun upload(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?, file: java.io.File, consent: Boolean): String
    suspend fun remove(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?)
    suspend fun download(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?): ByteArray
}
