package com.genusit.prms.domain

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
import com.genusit.prms.domain.usecase.DeletePropertyUseCase
import com.genusit.prms.domain.usecase.GetOwnerHomeUseCase
import com.genusit.prms.domain.usecase.PropertySelection
import com.genusit.prms.domain.usecase.SavePropertyUseCase
import com.genusit.prms.domain.usecase.SelectPropertyUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App 1.17: which property opens, the Home loader, select / add / delete, and the property form checks. */
class PropertySelectionTest {
    private val pune = Building(id = 1, name = "My Property", city = "Pune", label = "Pune – My Property", rooms = 7, occupied = 3, occupancyPct = 43)
    private val mumbai = Building(id = 2, name = "Sea View", city = "Mumbai", rooms = 4, occupied = 2, occupancyPct = 50)

    @Test fun serverChoiceWins() {
        val s = PropertySelection.resolve(BuildingPage(items = listOf(pune, mumbai), selectedId = 2, totalProperties = 2))
        assertEquals(SelectedProperty(2, "Mumbai – Sea View"), s)
    }

    @Test fun singlePropertyOwnerOpensIt() {
        assertEquals(SelectedProperty(1, "Pune – My Property"), PropertySelection.resolve(BuildingPage(items = listOf(pune), selectedId = 0, totalProperties = 1)))
    }

    @Test fun severalWithoutChoiceIsAllProperties() {
        assertEquals(SelectedProperty(0), PropertySelection.resolve(BuildingPage(items = listOf(pune, mumbai), totalProperties = 2)))
    }

    @Test fun olderServerHasNoProperties() {
        assertNull(PropertySelection.resolve(BuildingPage(totalProperties = 0)))
        assertTrue(PropertySelection.unsupported(ApiException("BAD_RESPONSE", "HTTP 404", 404)))
        assertTrue(PropertySelection.unsupported(ApiException("NOT_READY", "migrate", 503)))
        assertFalse(PropertySelection.unsupported(ApiException("PROPERTY_NOT_FOUND", "x", 404)))
        assertFalse(PropertySelection.unsupported(ApiException("NETWORK_ERROR", "x", 0)))
    }

    @Test fun titleFallsBackToCityAndName() {
        assertEquals("Mumbai – Sea View", mumbai.title)
        assertEquals("Pune – My Property", pune.title)
    }

    @Test fun formChecks() {
        assertTrue(BuildingForm.valid(BuildingInput("Shree Residency", "Pune", "pg")))
        assertFalse(BuildingForm.valid(BuildingInput(" ", "Pune")))
        assertFalse(BuildingForm.valid(BuildingInput("A", "")))
        assertFalse(BuildingForm.valid(BuildingInput("A", "Pune", pincode = "4110")))
        assertTrue(BuildingForm.valid(BuildingInput("A", "Pune", pincode = "411001")))
        assertFalse(BuildingForm.valid(BuildingInput("A", "Pune", type = "castle")))
        assertFalse(BuildingForm.valid(BuildingInput("x".repeat(151), "Pune")))
    }

    /* ── use cases with fakes ── */

    private class FakeScope : PropertyScope {
        val flow = MutableStateFlow<SelectedProperty?>(null)
        override val selected: StateFlow<SelectedProperty?> = flow
        override fun currentId(): Int? = flow.value?.id?.takeIf { it > 0 }
        override suspend fun set(selected: SelectedProperty?) { flow.value = selected }
    }

    private class FakeBuildings(var page: BuildingPage? = null, var fail: ApiException? = null) : BuildingsRepository {
        val selects = mutableListOf<Int>(); var created: BuildingInput? = null; var failSelect = false
        override suspend fun list(query: String, page: Int, perPage: Int, status: String, withDues: Boolean): BuildingPage { fail?.let { throw it }; return this.page!! }
        override suspend fun get(id: Int) = Building(id = id)
        override suspend fun create(input: BuildingInput): Building { created = input; return Building(id = 9, name = input.name, city = input.city) }
        override suspend fun update(id: Int, input: BuildingInput) {}
        override suspend fun delete(id: Int) {}
        override suspend fun select(id: Int) { if (failSelect) throw ApiException("NETWORK_ERROR", "x", 0); selects += id }
        override suspend fun moveRooms(roomIds: List<Int>, toBuildingId: Int) = roomIds.size
    }

    private class FakeReports(private val scope: FakeScope, private val property: Building?) : ReportRepository {
        var scopeSeen: Int? = -1
        override suspend fun ownerDashboard(): OwnerDashboard { scopeSeen = scope.currentId(); return OwnerDashboard(property = property) }
        override suspend fun collections(from: String?, to: String?) = throw NotImplementedError()
        override suspend fun revenue(from: String?, to: String?) = throw NotImplementedError()
        override suspend fun pending() = throw NotImplementedError()
        override suspend fun referrals() = throw NotImplementedError()
        override suspend fun vendorDashboard() = throw NotImplementedError()
    }

    @Test fun homeSetsTheScopeBeforeTheDashboard() = runTest {
        val scope = FakeScope()
        val reports = FakeReports(scope, mumbai)
        val d = GetOwnerHomeUseCase(reports, FakeBuildings(BuildingPage(items = listOf(pune), selectedId = 2, totalProperties = 2)), scope)()
        assertEquals(2, reports.scopeSeen)                       // the dashboard was asked for Sea View only
        assertEquals("Mumbai – Sea View", scope.flow.value?.label) // label from the dashboard (not on the first page)
        assertTrue(d.propertyCards.isEmpty()); assertEquals(2, d.totalProperties)
    }

    @Test fun homeAllPropertiesKeepsCards() = runTest {
        val scope = FakeScope()
        val reports = FakeReports(scope, null)
        val d = GetOwnerHomeUseCase(reports, FakeBuildings(BuildingPage(items = listOf(pune, mumbai), totalProperties = 2)), scope)()
        assertNull(reports.scopeSeen); assertEquals(2, d.propertyCards.size)
    }

    @Test fun homeOnOlderServerWorksAsBefore() = runTest {
        val scope = FakeScope().apply { flow.value = SelectedProperty(5, "old") }
        val reports = FakeReports(scope, null)
        val d = GetOwnerHomeUseCase(reports, FakeBuildings(fail = ApiException("BAD_RESPONSE", "HTTP 404", 404)), scope)()
        assertNull(scope.flow.value); assertNull(reports.scopeSeen); assertEquals(0, d.totalProperties)
    }

    @Test fun homeNetworkErrorIsShown() = runTest {
        val scope = FakeScope()
        assertFailsWith<ApiException> { GetOwnerHomeUseCase(FakeReports(scope, null), FakeBuildings(fail = ApiException("NETWORK_ERROR", "x", 0)), scope)() }
    }

    @Test fun selectRemembersOnServerAndRollsBackOnFailure() = runTest {
        val scope = FakeScope().apply { flow.value = SelectedProperty(1, "Pune") }
        val repo = FakeBuildings()
        SelectPropertyUseCase(repo, scope)(2, "Mumbai")
        assertEquals(listOf(2), repo.selects); assertEquals(2, scope.currentId())
        repo.failSelect = true
        assertFailsWith<ApiException> { SelectPropertyUseCase(repo, scope)(1, "Pune") }
        assertEquals(2, scope.currentId())
    }

    @Test fun newPropertyIsTrimmedAndSelected() = runTest {
        val scope = FakeScope(); val repo = FakeBuildings()
        val b = SavePropertyUseCase(repo, SelectPropertyUseCase(repo, scope))(0, BuildingInput("  Shree Residency ", " Pune ", "pg"))
        assertEquals("Shree Residency", repo.created?.name); assertEquals("Pune", repo.created?.city)
        assertEquals(9, b?.id); assertEquals(9, scope.currentId())
        assertFailsWith<IllegalArgumentException> { SavePropertyUseCase(repo, SelectPropertyUseCase(repo, scope))(0, BuildingInput("", "Pune")) }
    }

    @Test fun deletingTheOpenPropertyFallsBackToAll() = runTest {
        val scope = FakeScope().apply { flow.value = SelectedProperty(3, "x") }
        DeletePropertyUseCase(FakeBuildings(), scope)(3)
        assertEquals(SelectedProperty(0), scope.flow.value)
    }
}
