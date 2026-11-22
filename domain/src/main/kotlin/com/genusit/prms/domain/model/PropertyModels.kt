@file:UseSerializers(FlexDouble::class, FlexInt::class, FlexBool::class)

package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDouble
import com.genusit.prms.domain.util.FlexInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/*
 * 1.17 — Multi-property management (owner/buildings.php).
 * A "property" is a building / PG / hostel / complex that holds rooms. (The older "property" models in
 * Models.kt are rooms: their ids stay room ids.) The server decides every figure; nothing is counted here.
 */

/** One property with its room figures (rooms, occupied, available …) when the server sends them. */
@Serializable
data class Building(
    val id: Int = 0,
    val name: String = "",
    @SerialName("property_type") val type: String = "other",
    @SerialName("property_type_label") val typeLabel: String? = null,
    val city: String = "",
    val area: String? = null,
    val address: String? = null,
    val pincode: String? = null,
    val description: String? = null,
    val status: String = "active",
    /** "City – Name", as the website shows it. */
    val label: String? = null,
    @SerialName("has_image") val hasImage: Boolean = false,
    @SerialName("is_default") val isDefault: Boolean = false,
    val rooms: Int = 0,
    val occupied: Int = 0,
    val available: Int = 0,
    val maintenance: Int = 0,
    val tenants: Int = 0,
    @SerialName("occupancy_pct") val occupancyPct: Int = 0,
    @SerialName("pending_dues") val pendingDues: Double = 0.0,
) {
    val title: String get() = label?.takeIf { it.isNotBlank() } ?: listOf(city, name).filter { it.isNotBlank() }.joinToString(" – ")
    val active: Boolean get() = status == "active"
}

/** A page of the owner's properties (server-side search and paging, at most 50 per page). */
@Serializable
data class BuildingPage(
    val items: List<Building> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 20,
    val pages: Int = 1,
    /** The property remembered on the server (0 = All Properties), the same one the website opens. */
    @SerialName("selected_id") val selectedId: Int = 0,
    @SerialName("total_properties") val totalProperties: Int = 0,
) {
    val hasMore: Boolean get() = page < pages
}

/** Room / occupancy figures of the selected property (or of all properties). */
@Serializable
data class PropertySummary(
    @SerialName("total_rooms") val totalRooms: Int = 0,
    @SerialName("occupied_rooms") val occupiedRooms: Int = 0,
    @SerialName("available_rooms") val vacantRooms: Int = 0,
    @SerialName("maintenance_rooms") val maintenanceRooms: Int = 0,
    @SerialName("occupancy_pct") val occupancyPct: Int = 0,
    val tenants: Int = 0,
    @SerialName("month_collection") val monthCollection: Double = 0.0,
    @SerialName("pending_rent") val pendingRent: Double = 0.0,
    @SerialName("bills_this_month") val billsThisMonth: Int = 0,
    @SerialName("pending_bills") val pendingBills: Int = 0,
    @SerialName("open_complaints") val openComplaints: Int = 0,
    @SerialName("employees_assigned") val employeesAssigned: Int = 0,
    /** Only with All Properties. */
    val properties: Int? = null,
)

/** What the owner types on the Add / Edit Property screen. */
data class BuildingInput(
    val name: String,
    val city: String,
    val type: String = "other",
    val area: String = "",
    val address: String = "",
    val pincode: String = "",
    val description: String = "",
    /** Only when editing: "active" / "inactive". */
    val status: String? = null,
)

/** The property the owner works in. [id] 0 = All Properties. */
data class SelectedProperty(val id: Int, val label: String = "")

object PropertyTypes {
    val all = listOf("apartment", "pg", "hostel", "commercial", "other")
}

/** Checks of the Add / Edit Property form (the server checks again). */
object BuildingForm {
    fun nameOk(v: String) = v.isNotBlank() && v.trim().length <= 150
    fun cityOk(v: String) = v.isNotBlank() && v.trim().length <= 100
    fun pincodeOk(v: String) = v.isBlank() || Regex("\\d{6}").matches(v.trim())
    fun valid(i: BuildingInput) = nameOk(i.name) && cityOk(i.city) && pincodeOk(i.pincode) && i.type in PropertyTypes.all
}
