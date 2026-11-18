@file:UseSerializers(FlexDouble::class, FlexInt::class, FlexBool::class)

package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDouble
import com.genusit.prms.domain.util.FlexDoubleOrNull
import com.genusit.prms.domain.util.FlexInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/*
 * 1.16 — Manage Charges for an allocated room (GET/POST/PUT owner/room_charges.php).
 * The server decides which charges are on each bill (start date, one-time charges, stopped charges);
 * these models only carry what the screen shows and what the owner changes.
 */

@Serializable
data class RoomChargesData(
    /** False until the server's database has the 1.16 columns (changes are refused until then). */
    val ready: Boolean = true,
    val allocation: RoomChargeAllocation = RoomChargeAllocation(),
    val charges: List<RoomCharge> = emptyList(),
    /** Active Charge Master entries; [RoomChargeOption.onRoom] = already on this room. */
    val available: List<RoomChargeOption> = emptyList(),
    val today: String? = null,
) {
    /** Charges that can still be added to this room. */
    val addable: List<RoomChargeOption> get() = available.filter { !it.onRoom }
}

@Serializable
data class RoomChargeAllocation(
    val id: Int = 0,
    val status: String = "active",
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    @SerialName("base_rent") val baseRent: Double = 0.0,
) {
    val isActive: Boolean get() = status == "active"
}

/** One charge of the room. [state] = active | scheduled | stopping | stopped | billed; [stateLabel] is translated by the server. */
@Serializable
data class RoomCharge(
    val id: Int = 0,
    @SerialName("charge_id") val chargeId: Int = 0,
    val name: String = "",
    @SerialName("charge_type") val chargeType: String = "fixed",
    @Serializable(with = FlexDoubleOrNull::class) val amount: Double? = null,
    @SerialName("master_amount") @Serializable(with = FlexDoubleOrNull::class) val masterAmount: Double? = null,
    @SerialName("own_amount") val ownAmount: Boolean = false,
    @SerialName("rate_per_unit") @Serializable(with = FlexDoubleOrNull::class) val ratePerUnit: Double? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    val frequency: String = "monthly",
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    val remarks: String? = null,
    val state: String = "active",
    @SerialName("state_label") val stateLabel: String? = null,
    @SerialName("billed_invoice_number") val billedInvoiceNumber: String? = null,
    @SerialName("master_active") val masterActive: Boolean = true,
    val editable: Boolean = false,
    @SerialName("can_restart") val canRestart: Boolean = false,
) {
    val isMeter: Boolean get() = chargeType == "variable"
    val isOneTime: Boolean get() = frequency == "one_time"
    /** Still billed until its last date (start again = remove the stop, no date needed). */
    val isStopping: Boolean get() = state == "stopping"
    val isOn: Boolean get() = state == "active" || state == "scheduled"
}

@Serializable
data class RoomChargeOption(
    val id: Int = 0,
    val name: String = "",
    @SerialName("charge_type") val chargeType: String = "fixed",
    @SerialName("fixed_amount") @Serializable(with = FlexDoubleOrNull::class) val fixedAmount: Double? = null,
    @SerialName("rate_per_unit") @Serializable(with = FlexDoubleOrNull::class) val ratePerUnit: Double? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    @SerialName("on_room") val onRoom: Boolean = false,
) {
    val isMeter: Boolean get() = chargeType == "variable"
}

/**
 * Add a charge to the room: an existing Charge Master entry ([chargeId]) or a new one ([newCharge], which the server
 * also adds to Charge Master). [amount] = this room's amount of a fixed charge (null = Charge Master amount).
 */
data class RoomChargeInput(
    val chargeId: Int? = null,
    val newCharge: NewCharge? = null,
    val amount: Double? = null,
    val frequency: String = FREQ_MONTHLY,
    val startDate: String,
    val remarks: String = "",
)

/** Change an active / upcoming room charge. */
data class RoomChargeChange(val amount: Double?, val frequency: String, val startDate: String, val remarks: String)

const val FREQ_MONTHLY = "monthly"
const val FREQ_ONE_TIME = "one_time"
