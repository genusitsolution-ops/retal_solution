package com.genusit.prms.domain

import com.genusit.prms.domain.model.AppConfig
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.FixedChargeLine
import com.genusit.prms.domain.model.InvoiceContext
import com.genusit.prms.domain.model.InvoiceItem
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.PropertyType
import com.genusit.prms.domain.model.VariableChargeLine
import com.genusit.prms.domain.usecase.BillCalculator
import com.genusit.prms.domain.usecase.MeterCalc
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.15.1: the same figures as the server suite (test_v1151.py): Charge Master ₹13, "Change rate for this bill?" ₹15 / ₹16. */
class MeterRatesTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun chargeMasterRateAndRateForThisBill() {
        assertEquals(260.0, MeterCalc.calc(20.0, 13.0).amount)
        assertFalse(MeterCalc.calc(20.0, 13.0).changed)
        val o15 = MeterCalc.calc(20.0, 13.0, 15.0)
        assertEquals(300.0, o15.amount); assertTrue(o15.changed); assertEquals(15.0, o15.rate)
        assertEquals(400.0, MeterCalc.calc(20.0, 13.0, 20.0).amount)
        assertFalse(MeterCalc.calc(20.0, 13.0, 13.0).changed, "typing the same rate is not a change")
        assertEquals(0.0, MeterCalc.calc(0.0, 13.0).amount)
        assertEquals(136.5, MeterCalc.calc(10.5, 13.0).amount)
        assertEquals(0.0, MeterCalc.calc(-5.0, 13.0).amount, "negative units never bill")
    }

    @Test fun billPreviewUsesChargeMasterUnlessChanged() {
        val ctx = InvoiceContext(baseRent = 5000.0,
            fixedCharges = listOf(FixedChargeLine(1, "Maintenance", 150.0), FixedChargeLine(2, "Water", 100.0)),
            variableCharges = listOf(VariableChargeLine(3, "Electricity", 13.0, "unit", lastReading = 1000.0),
                VariableChargeLine(4, "Electricity B", 14.0, "unit", lastReading = 500.0)))
        var sel = BillCalculator.defaults(ctx).copy(readings = mapOf(3 to (1000.0 to 1020.0), 4 to (500.0 to 520.0)))
        assertEquals(5000.0 + 250 + 260 + 280, BillCalculator.total(BillCalculator.lines(ctx, sel, "Base rent")))
        assertNull(BillCalculator.readings(ctx, sel).first { it.tcId == 3 }.rate, "unticked → no rate sent, server uses Charge Master")
        sel = sel.copy(rates = mapOf(3 to 15.0, 4 to null))
        val lines = BillCalculator.lines(ctx, sel, "Base rent")
        assertEquals(5550.0 + 280, BillCalculator.total(lines))
        assertTrue(lines.first { it.name == "Electricity" }.rateChanged)
        assertFalse(lines.first { it.name == "Electricity B" }.rateChanged)
        assertEquals(15.0, BillCalculator.readings(ctx, sel).first { it.tcId == 3 }.rate)
        assertNull(BillCalculator.readings(ctx, sel).first { it.tcId == 4 }.rate)
        assertEquals(300.0, BillCalculator.meterAmount(1020.0, 1040.0, 13.0, 15.0)!!.amount)
        assertEquals(260.0, BillCalculator.meterAmount(1020.0, 1040.0, 13.0, null)!!.amount)
        assertNull(BillCalculator.meterAmount(1040.0, 1030.0, 13.0, null), "lower reading → no amount")
    }

    @Test fun parsesServerAnswers() {
        val c = json.decodeFromString(ListSerializer(Charge.serializer()),
            """[{"id":"4","name":"Light bill","charge_type":"variable","rate_per_unit":"13.0000","unit_label":"unit","status":"active",
                "slabs":[{"upto":90,"rate":13},{"upto":null,"rate":20}],"rate_mode":"slab_progressive"}]""")
        assertEquals(13.0, c[0].ratePerUnit, "a 1.15 server answer with slab fields still reads; the rate per unit is used"); assertTrue(c[0].isVariable)
        val it = json.decodeFromString(InvoiceItem.serializer(), """{"id":"9","item_type":"variable_charge","description":"Electricity (20 unit @ ₹15)","quantity":"20.000",
            "unit_price":"15.0000","amount":"300.00","meter":{"prev_reading":1020,"curr_reading":1040,"units":20,"rate":15,"rate_changed":true,
            "default_rate":13,"lines":["Previous Reading: 1020","Current Reading: 1040","Units Used: 20","Rate: ₹15/unit (changed for this bill)"]}}""")
        assertTrue(it.meter!!.rateChanged); assertEquals(4, it.meter!!.lines.size)
        val pre115 = json.decodeFromString(InvoiceItem.serializer(), """{"id":1,"item_type":"variable_charge","description":"x","quantity":1,"unit_price":1,"amount":1,"meter":null}""")
        assertNull(pre115.meter)
        val p = json.decodeFromString(Property.serializer(), """{"id":5,"property_code":"Room 201","property_type_id":"7","property_type_name":"Studio Apartment","property_type_label":"स्टूडियो"}""")
        assertEquals(7, p.typeId); assertEquals("स्टूडियो", p.typeLabel)
        val t = json.decodeFromString(ListSerializer(PropertyType.serializer()), """[{"id":7,"name":"Studio Apartment","label":"Studio Apartment","active":false}]""")
        assertFalse(t[0].active)
        val cfg = json.decodeFromString(AppConfig.serializer(), """{"version":3,"login_help":{"title":"Need Help?","message":"Having trouble getting started? Our team is here to help you.",
            "phone":"9876500022","whatsapp":"9876500033","tel_url":"tel:+919876500022","whatsapp_url":"https://wa.me/919876500033?text=Hi"}}""")
        assertEquals("tel:+919876500022", cfg.loginHelp!!.telUrl)
        assertNull(json.decodeFromString(AppConfig.serializer(), """{"version":3,"login_help":null}""").loginHelp)
    }
}
