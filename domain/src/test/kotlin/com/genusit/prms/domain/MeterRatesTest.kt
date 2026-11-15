package com.genusit.prms.domain

import com.genusit.prms.domain.model.AppConfig
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.FixedChargeLine
import com.genusit.prms.domain.model.InvoiceContext
import com.genusit.prms.domain.model.InvoiceItem
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.PropertyType
import com.genusit.prms.domain.model.Slab
import com.genusit.prms.domain.model.VariableChargeLine
import com.genusit.prms.domain.usecase.BillCalculator
import com.genusit.prms.domain.usecase.MeterCalc
import com.genusit.prms.domain.usecase.SlabProblem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.15: the same figures as the server suite (test_v115.py): ₹13 / ₹15 / ₹20, slabs 1–10 ₹13, 11–30 ₹14, 31–50 ₹15, above ₹18. */
class MeterRatesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }
    private val slabs = listOf(Slab(10.0, 13.0), Slab(30.0, 14.0), Slab(50.0, 15.0), Slab(null, 18.0))

    @Test fun flatRateAndBillOnlyRate() {
        assertEquals(260.0, MeterCalc.calc(20.0, "flat", emptyList(), 13.0).amount)
        val o15 = MeterCalc.calc(20.0, "flat", emptyList(), 13.0, 15.0)
        assertEquals(300.0, o15.amount); assertTrue(o15.changed)
        assertEquals(400.0, MeterCalc.calc(20.0, "flat", emptyList(), 13.0, 20.0).amount)
        assertFalse(MeterCalc.calc(20.0, "flat", emptyList(), 13.0, 13.0).changed, "typing the same rate is not a change")
    }

    @Test fun slabs() {
        assertEquals(280.0, MeterCalc.calc(20.0, "slab_single", slabs, 13.0).amount)
        assertEquals(130.0, MeterCalc.calc(10.0, "slab_single", slabs, 13.0).amount)
        assertEquals(1080.0, MeterCalc.calc(60.0, "slab_single", slabs, 13.0).amount)
        val p = MeterCalc.calc(20.0, "slab_progressive", slabs, 13.0)
        assertEquals(270.0, p.amount); assertEquals(listOf(10.0, 10.0), p.parts.map { it.units })
        assertEquals(890.0, MeterCalc.calc(60.0, "slab_progressive", slabs, 13.0).amount)
        assertEquals(0.0, MeterCalc.calc(0.0, "slab_progressive", slabs, 13.0).amount)
        assertEquals(137.0, MeterCalc.calc(10.5, "slab_progressive", slabs, 13.0).amount)
        val o = MeterCalc.calc(20.0, "slab_progressive", slabs, 13.0, 16.0)
        assertEquals(320.0, o.amount); assertTrue(o.changed)
        // a slab mode without slabs falls back to the flat rate, as on the server
        assertEquals(260.0, MeterCalc.calc(20.0, "slab_progressive", emptyList(), 13.0).amount)
    }

    @Test fun slabChecks() {
        assertNull(MeterCalc.slabProblem(slabs))
        assertEquals(SlabProblem.TooFew, MeterCalc.slabProblem(listOf(Slab(null, 5.0))))
        assertEquals(SlabProblem.NotIncreasing, MeterCalc.slabProblem(listOf(Slab(10.0, 1.0), Slab(5.0, 2.0), Slab(null, 3.0))))
        assertEquals(SlabProblem.LastHasUnits, MeterCalc.slabProblem(listOf(Slab(10.0, 1.0), Slab(20.0, 2.0))))
        assertEquals(SlabProblem.BadRate, MeterCalc.slabProblem(listOf(Slab(10.0, -1.0), Slab(null, 2.0))))
        assertEquals(listOf("1–10", "11–30", "31–50", "Above 50"), MeterCalc.ranges(slabs) { "Above $it" })
    }

    @Test fun billPreviewUsesRatesAndSendsTheBillRate() {
        val ctx = InvoiceContext(baseRent = 5000.0,
            fixedCharges = listOf(FixedChargeLine(1, "Maintenance", 150.0), FixedChargeLine(2, "Water", 100.0)),
            variableCharges = listOf(VariableChargeLine(3, "Electricity", 13.0, "unit", lastReading = 1000.0),
                VariableChargeLine(4, "Slab light", 13.0, "unit", lastReading = 500.0, rateMode = "slab_progressive", slabs = slabs)))
        var sel = BillCalculator.defaults(ctx).copy(readings = mapOf(3 to (1000.0 to 1020.0), 4 to (500.0 to 520.0)))
        assertEquals(5000.0 + 250 + 260 + 270, BillCalculator.total(BillCalculator.lines(ctx, sel, "Base rent")))
        sel = sel.copy(rates = mapOf(3 to 15.0))
        val lines = BillCalculator.lines(ctx, sel, "Base rent")
        assertEquals(5550.0 + 270, BillCalculator.total(lines))
        assertTrue(lines.first { it.name == "Electricity" }.rateChanged)
        assertEquals(15.0, BillCalculator.readings(ctx, sel).first { it.tcId == 3 }.rate)
        assertNull(BillCalculator.readings(ctx, sel).first { it.tcId == 4 }.rate, "no typed rate → Charge Master")
        assertEquals(300.0, BillCalculator.meterAmount(1020.0, 1040.0, "flat", emptyList(), 13.0, 15.0)!!.amount)
        assertNull(BillCalculator.meterAmount(1040.0, 1030.0, "flat", emptyList(), 13.0, null), "lower reading → no amount")
    }

    @Test fun parsesServerAnswers() {
        val c = json.decodeFromString(ListSerializer(Charge.serializer()),
            """[{"id":"4","name":"Electricity Slab","charge_type":"variable","rate_per_unit":"13.0000","unit_label":"unit","status":"active",
                "slabs":[{"upto":10,"rate":13},{"upto":30,"rate":14},{"upto":null,"rate":18}],"rate_mode":"slab_progressive",
                "rate_mode_label":"Each slab at its own rate","slabs_text":"1–10: ₹13 · 11–30: ₹14 · Above 30: ₹18"}]""")
        assertTrue(c[0].hasSlabs); assertEquals(null, c[0].slabs[2].upto); assertEquals(14.0, c[0].slabs[1].rate)
        val old = json.decodeFromString(Charge.serializer(), """{"id":3,"name":"Electricity","charge_type":"variable","rate_per_unit":"13.00"}""")
        assertEquals("flat", old.rateMode); assertFalse(old.hasSlabs)
        val it = json.decodeFromString(InvoiceItem.serializer(), """{"id":"9","item_type":"variable_charge","description":"Electricity (20 unit @ ₹15)","quantity":"20.000",
            "unit_price":"15.0000","amount":"300.00","meter":{"prev_reading":1020,"curr_reading":1040,"units":20,"rate":15,"rate_mode":"flat","rate_changed":true,
            "default_rate":13,"slabs":[],"lines":["Previous Reading: 1020","Current Reading: 1040","Units Used: 20","Rate: ₹15/unit (changed for this bill)"]}}""")
        assertTrue(it.meter!!.rateChanged); assertEquals(4, it.meter!!.lines.size)
        val pre115 = json.decodeFromString(InvoiceItem.serializer(), """{"id":1,"item_type":"variable_charge","description":"x","quantity":1,"unit_price":1,"amount":1,"meter":null}""")
        assertNull(pre115.meter)
        val p = json.decodeFromString(Property.serializer(), """{"id":5,"property_code":"Room 201","property_type_id":"7","property_type_name":"Studio Apartment","property_type_label":"स्टूडियो"}""")
        assertEquals(7, p.typeId); assertEquals("स्टूडियो", p.typeLabel)
        val t = json.decodeFromString(ListSerializer(PropertyType.serializer()), """[{"id":7,"name":"Studio Apartment","label":"Studio Apartment","active":false}]""")
        assertFalse(t[0].active)
        val cfg = json.decodeFromString(AppConfig.serializer(), """{"version":3,"login_help":{"title":"Need Help with Rental Solution?","message":"We are here to help!",
            "phone":"9876500022","whatsapp":"9876500033","tel_url":"tel:+919876500022","whatsapp_url":"https://wa.me/919876500033?text=Hi"}}""")
        assertEquals("tel:+919876500022", cfg.loginHelp!!.telUrl)
        assertNull(json.decodeFromString(AppConfig.serializer(), """{"version":3,"login_help":null}""").loginHelp)
    }
}
