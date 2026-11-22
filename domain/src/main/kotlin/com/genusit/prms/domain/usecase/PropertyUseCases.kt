package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.Building
import com.genusit.prms.domain.model.BuildingForm
import com.genusit.prms.domain.model.BuildingInput
import com.genusit.prms.domain.model.BuildingPage
import com.genusit.prms.domain.model.OwnerDashboard
import com.genusit.prms.domain.model.SelectedProperty
import com.genusit.prms.domain.repository.BuildingsRepository
import com.genusit.prms.domain.repository.PropertyScope
import com.genusit.prms.domain.repository.ReportRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/* 1.17 — multi-property management. */

object PropertySelection {
    /**
     * Which property to open, from the server's list answer: the property remembered on the server (the same one
     * the website opens), the only property of a single-property owner, otherwise All Properties (id 0).
     * null = the server has no multi-property support (older server): nothing is filtered, as in 1.16.
     */
    fun resolve(page: BuildingPage): SelectedProperty? {
        if (page.totalProperties <= 0) return null
        if (page.selectedId > 0) return SelectedProperty(page.selectedId, page.items.firstOrNull { it.id == page.selectedId }?.title.orEmpty())
        if (page.totalProperties == 1) page.items.firstOrNull()?.let { return SelectedProperty(it.id, it.title) }
        return SelectedProperty(0)
    }

    /** Errors meaning "this server has no properties API yet" (app updated before the website). */
    fun unsupported(e: ApiException) = e.code == "NOT_READY" || e.httpStatus == 404 && e.code != "PROPERTY_NOT_FOUND"
}

/**
 * Home: first the owner's properties (which one is selected, the first few for the All Properties view), then
 * the dashboard of the selected property. An older server without properties gives the 1.16 dashboard.
 */
class GetOwnerHomeUseCase @Inject constructor(
    private val reports: ReportRepository,
    private val buildings: BuildingsRepository,
    private val scope: PropertyScope,
) {
    suspend operator fun invoke(): OwnerDashboard {
        val page = try {
            buildings.list(page = 1, perPage = 5)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (!PropertySelection.unsupported(e)) throw e
            null
        }
        if (page == null) {
            scope.set(null)
            return reports.ownerDashboard()
        }
        scope.set(PropertySelection.resolve(page))
        val d = reports.ownerDashboard()
        // the label of the selected property comes with the dashboard (it may not be on the first page)
        d.property?.let { scope.set(SelectedProperty(it.id, it.title)) }
        return d.copy(propertyCards = if ((scope.currentId() ?: 0) == 0) page.items else emptyList(), totalProperties = page.totalProperties)
    }
}

class ListPropertiesUseCase @Inject constructor(private val repo: BuildingsRepository) {
    suspend operator fun invoke(query: String, page: Int, status: String = "active") = repo.list(query.trim(), page, 20, status, withDues = false)
}

class GetPropertyUseCase @Inject constructor(private val repo: BuildingsRepository) {
    suspend operator fun invoke(id: Int) = repo.get(id)
}

/** Switches the property for the whole app and remembers it on the server; every open screen reloads. */
class SelectPropertyUseCase @Inject constructor(private val repo: BuildingsRepository, private val scope: PropertyScope) {
    suspend operator fun invoke(id: Int, label: String) {
        val before = scope.selected.value
        scope.set(SelectedProperty(id, label))
        try {
            repo.select(id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            scope.set(before)
            throw e
        }
    }
}

/** Adds a property and opens it (the next room or tenant goes into it). */
class SavePropertyUseCase @Inject constructor(private val repo: BuildingsRepository, private val select: SelectPropertyUseCase) {
    suspend operator fun invoke(id: Int, input: BuildingInput): Building? {
        val clean = input.copy(name = input.name.trim(), city = input.city.trim(), area = input.area.trim(),
            address = input.address.trim(), pincode = input.pincode.trim(), description = input.description.trim())
        require(BuildingForm.valid(clean)) { "invalid property" }
        if (id > 0) { repo.update(id, clean); return null }
        val b = repo.create(clean)
        select(b.id, b.title)
        return b
    }
}

class DeletePropertyUseCase @Inject constructor(private val repo: BuildingsRepository, private val scope: PropertyScope) {
    suspend operator fun invoke(id: Int) {
        repo.delete(id)
        if (scope.currentId() == id) scope.set(SelectedProperty(0))
    }
}
