package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PropertyScopeProvider
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.BuildingsRepositoryImpl
import com.genusit.prms.data.repository.ReportRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.BuildingInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App 1.17: ?building_id on owner requests, the properties API (owner/buildings.php) and old-server behaviour. */
class PropertyScopeApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: PrmsApiService
    private lateinit var buildings: BuildingsRepositoryImpl
    private lateinit var reports: ReportRepositoryImpl
    private var selected: Int? = null
    private val unknown = mutableListOf<Int>()

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val scope = object : PropertyScopeProvider {
            override fun buildingId() = selected
            override fun onUnknown(id: Int) { unknown += id; selected = null }
        }
        api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }, propertyScope = scope), json))
        buildings = BuildingsRepositoryImpl(api, ApiCaller(json)); reports = ReportRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    private val dash = """{"status":"success","data":{"summary":{"properties":4,"occupied_properties":2,"tenants":2},
        "property":{"id":2,"name":"Sea View","city":"Mumbai","label":"Mumbai – Sea View","property_type":"apartment","property_type_label":"Apartment","status":"active"},
        "property_summary":{"total_rooms":"4","occupied_rooms":2,"available_rooms":2,"maintenance_rooms":0,"occupancy_pct":50,"tenants":2,"month_collection":"3000.00","pending_rent":13000,"properties":null},
        "properties_count":3}}"""

    @Test fun ownerRequestsCarryTheSelectedProperty() = runTest {
        selected = 2
        enqueue(200, dash)
        val d = reports.ownerDashboard()
        assertEquals("/api/v1/owner/dashboard.php?building_id=2", server.takeRequest().path)
        assertEquals("Mumbai – Sea View", d.property?.title); assertEquals(50, d.propertySummary?.occupancyPct)
        assertEquals(3000.0, d.propertySummary?.monthCollection); assertEquals(4, d.propertySummary?.totalRooms); assertEquals(3, d.propertiesCount)
    }

    @Test fun allPropertiesSendsNothing() = runTest {
        selected = null
        enqueue(200, """{"status":"success","data":{"summary":{}}}""")
        val d = reports.ownerDashboard()
        assertEquals("/api/v1/owner/dashboard.php", server.takeRequest().path)
        assertNull(d.property); assertNull(d.propertySummary)   // 1.16 server: no new keys, still decodes
    }

    @Test fun propertyListAndOtherRolesAreNotFiltered() = runTest {
        selected = 2
        enqueue(200, """{"status":"success","data":{"items":[{"id":1,"name":"My Property","city":"Pune","rooms":"7","occupied":3,"available":3,"occupancy_pct":43,"is_default":1,"has_image":0}],
            "total":31,"page":1,"per_page":20,"pages":2,"selected_id":"2","total_properties":31}}""")
        val p = buildings.list("sea", 1, 20)
        assertEquals("/api/v1/owner/buildings.php?q=sea&page=1&per_page=20&status=active", server.takeRequest().path)
        assertEquals(2, p.selectedId); assertTrue(p.hasMore); assertEquals(7, p.items[0].rooms); assertTrue(p.items[0].isDefault)
        enqueue(200, """{"status":"success","data":{}}""")
        api.agentDashboard()
        assertEquals("/api/v1/employee/dashboard.php", server.takeRequest().path)
    }

    @Test fun explicitBuildingIdIsKept() {
        val u = okhttp3.HttpUrl.Builder().scheme("https").host("x.test").addPathSegments("api/v1/owner/tenants.php").addQueryParameter("building_id", "5").build()
        assertTrue(!com.genusit.prms.data.remote.PropertyScopeInterceptor.applies(u))
        val v = okhttp3.HttpUrl.Builder().scheme("https").host("x.test").addPathSegments("api/v1/owner/tenants.php").build()
        assertTrue(com.genusit.prms.data.remote.PropertyScopeInterceptor.applies(v))
    }

    @Test fun deletedPropertyFallsBackToAllProperties() = runTest {
        selected = 7
        enqueue(404, """{"status":"error","code":"PROPERTY_NOT_FOUND","message":"Property not found."}""")
        enqueue(200, """{"status":"success","data":{"summary":{"properties":11}}}""")
        val d = reports.ownerDashboard()
        assertEquals("/api/v1/owner/dashboard.php?building_id=7", server.takeRequest().path)
        assertEquals("/api/v1/owner/dashboard.php", server.takeRequest().path)
        assertEquals(listOf(7), unknown); assertEquals(11, d.summary.properties); assertNull(selected)
    }

    @Test fun createSelectMoveAndErrors() = runTest {
        enqueue(201, """{"status":"success","data":{"id":9,"name":"Shree Residency","city":"Pune","label":"Pune – Shree Residency","property_type":"pg"}}""")
        val b = buildings.create(BuildingInput("Shree Residency", "Pune", "pg", pincode = "411001"))
        val r = server.takeRequest()
        assertEquals("POST", r.method)
        val body = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("Shree Residency", body["name"]!!.jsonPrimitive.content); assertEquals("pg", body["property_type"]!!.jsonPrimitive.content)
        assertEquals(9, b.id); assertEquals("Pune – Shree Residency", b.title)

        enqueue(200, """{"status":"success","data":{"selected_id":0}}""")
        buildings.select(0)
        assertEquals("/api/v1/owner/buildings.php?select=0", server.takeRequest().path)

        enqueue(200, """{"status":"success","data":{"moved":2}}""")
        assertEquals(2, buildings.moveRooms(listOf(201, 202), 9))
        val m = server.takeRequest()
        assertEquals("/api/v1/owner/buildings.php?move=1", m.path)
        val mb = Json.parseToJsonElement(m.body.readUtf8()).jsonObject
        assertEquals(listOf(201, 202), mb["room_ids"]!!.jsonArray.map { it.jsonPrimitive.int }); assertEquals(9, mb["to_building_id"]!!.jsonPrimitive.int)

        enqueue(409, """{"status":"error","code":"DUPLICATE","message":"You already have a property named Sea View in Mumbai."}""")
        val e = assertFailsWith<ApiException> { buildings.create(BuildingInput("Sea View", "Mumbai")) }
        assertEquals("DUPLICATE", e.code); assertEquals(409, e.httpStatus)
    }

    @Test fun olderServerWithoutTheApi() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("<html>Not Found</html>"))
        val e = assertFailsWith<ApiException> { buildings.list() }
        assertEquals(404, e.httpStatus)
        assertTrue(com.genusit.prms.domain.usecase.PropertySelection.unsupported(e))
    }

    @Test fun reportFiguresDecode() = runTest {
        enqueue(200, """{"status":"success","data":{"total_rooms":12,"occupied_rooms":5,"available_rooms":6,"maintenance_rooms":1,"occupancy_pct":42,
            "active_tenants":"5","month_billed":"26000.00","month_collected":8000,"month_collection":8000,"pending_rent":"18000.00","overdue_amount":"5000.50","overdue_bills":1,"collection_pct":31,"properties":3}}""")
        val s = buildings.summary(null)
        assertEquals("/api/v1/owner/buildings.php?summary=1", server.takeRequest().path)
        assertEquals(5, s.activeTenants); assertEquals(26000.0, s.monthBilled); assertEquals(8000.0, s.collected); assertEquals(5000.5, s.overdueAmount); assertEquals(31, s.collectionPct)
        enqueue(200, """{"status":"success","data":{"total_rooms":4,"property":{"id":2,"name":"Sea View","city":"Mumbai"}}}""")
        assertEquals("Mumbai – Sea View", buildings.summary(2).property?.title)
        assertEquals("/api/v1/owner/buildings.php?summary=1&building_id=2", server.takeRequest().path)
        enqueue(200, """{"status":"success","data":{"items":[{"id":2,"name":"Sea View","city":"Mumbai","pending_dues":"13000.00","overdue":"0","month_billed":16000,"month_collected":"3000","active_tenants":2}],"total":1,"page":1,"pages":1,"total_properties":3}}""")
        val p = buildings.list(page = 1, perPage = 20, status = "all", withDues = true)
        assertEquals("/api/v1/owner/buildings.php?page=1&per_page=20&status=all&dues=1", server.takeRequest().path)
        assertEquals(13000.0, p.items[0].pendingDues); assertEquals(3000.0, p.items[0].monthCollected); assertEquals(2, p.items[0].activeTenants)
    }
}
