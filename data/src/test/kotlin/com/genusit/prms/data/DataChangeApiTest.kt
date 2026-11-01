package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.AgentSettlementRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.HandoverMethod
import com.genusit.prms.domain.repository.DataChangeBus
import com.genusit.prms.domain.repository.DataTopic
import com.genusit.prms.domain.repository.SharedFlowDataChangeBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** 1.10: a write the server accepted tells open screens to reload; reads and rejected writes do not. */
class DataChangeApiTest {
    private class RecordingBus : DataChangeBus {
        val published = mutableListOf<Set<DataTopic>>()
        override val changes: SharedFlow<Set<DataTopic>> = MutableSharedFlow()
        override fun publish(vararg topics: DataTopic) { published += topics.toSet() }
    }

    private lateinit var server: MockWebServer
    private lateinit var bus: RecordingBus
    private lateinit var caller: ApiCaller
    private lateinit var agent: AgentSettlementRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        bus = RecordingBus(); caller = ApiCaller(json, bus)
        agent = AgentSettlementRepositoryImpl(api, caller)
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }

    @Test fun readDoesNotPublishButAcceptedWriteDoes() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"balance":{"collections":1,"collected":"2000","confirmed":"0","awaiting_confirmation":0,"outstanding":2000,"available":2000},"open_collections":[],"methods":{"cash":"Cash"}}}""")
        agent.balance()
        assertTrue(bus.published.isEmpty(), "GET must not trigger reloads")

        enqueue(422, """{"status":"error","code":"AMOUNT_EXCEEDS_BALANCE","message":"Too much"}""")
        try { agent.submit(HandoverInput(2500.0, emptyList(), HandoverMethod.CASH, "2026-11-01", "", ""), "K1"); fail() } catch (e: ApiException) {}
        assertTrue(bus.published.isEmpty(), "a rejected write changes nothing")

        enqueue(201, """{"status":"success","message":"Success","data":{"id":9,"settlement_number":"ST-1","owner_id":1,"employee_id":1,"amount_submitted":500,"amount_confirmed":null,"method":"cash","reference_no":null,"handover_date":"2026-11-01","remarks":null,"status":"submitted","review_note":null,"reviewed_at":null,"created_by_type":"employee","created_at":"2026-11-01 10:00:00","updated_at":"2026-11-01 10:00:00","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Submitted","method_label":"Cash","items":[],"audit":[]}}""")
        agent.submit(HandoverInput(500.0, emptyList(), HandoverMethod.CASH, "2026-11-01", "", ""), "K2")
        assertEquals(1, bus.published.size)
        assertTrue(DataTopic.HANDOVERS in bus.published[0] && DataTopic.COLLECTIONS in bus.published[0])
    }

    @Test fun topicsByEndpoint() {
        assertEquals(setOf(DataTopic.BILLING, DataTopic.COLLECTIONS, DataTopic.HANDOVERS), caller.topicsFor("/api/v1/owner/payments.php"))
        assertEquals(setOf(DataTopic.BILLING, DataTopic.COLLECTIONS, DataTopic.HANDOVERS), caller.topicsFor("/api/v1/employee/collections.php"))
        assertEquals(null, caller.topicsFor("/api/v1/auth/login.php"))
        assertEquals(DataTopic.entries.toSet(), caller.topicsFor("/api/v1/owner/something_new.php"))
    }

    @Test fun busDeliversToCollectors() = runTest {
        val real = SharedFlowDataChangeBus()
        val got = mutableListOf<Set<DataTopic>>()
        val job = launch(kotlinx.coroutines.Dispatchers.Unconfined) { real.changes.collect { got += it } }
        real.publish(DataTopic.BILLING)
        real.publish()
        job.cancel()
        assertEquals(listOf(setOf(DataTopic.BILLING)), got)
    }
}
