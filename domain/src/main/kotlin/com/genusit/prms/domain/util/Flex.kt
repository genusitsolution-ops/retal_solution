package com.genusit.prms.domain.util

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * The PHP API returns MySQL DECIMAL/INT columns sometimes as JSON numbers and sometimes as
 * strings ("3269.00"), and flags as true/false or 1/0. These serializers accept every form so
 * the app never crashes on a type difference between endpoints.
 */
object FlexDouble : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        if (el is JsonNull) return 0.0
        val p = el as? JsonPrimitive ?: return 0.0
        return p.doubleOrNull ?: p.content.trim().toDoubleOrNull() ?: 0.0
    }
}

object FlexInt : KSerializer<Int> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeInt()
        if (el is JsonNull) return 0
        val p = el as? JsonPrimitive ?: return 0
        return p.longOrNull?.toInt() ?: p.content.trim().toDoubleOrNull()?.toInt() ?: 0
    }
}

object FlexBool : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexBool", PrimitiveKind.BOOLEAN)
    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
    override fun deserialize(decoder: Decoder): Boolean {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeBoolean()
        val p = el as? JsonPrimitive ?: return false
        if (el is JsonNull) return false
        p.booleanOrNull?.let { return it }
        return when (p.content.trim().lowercase()) { "1", "yes", "true" -> true; else -> false }
    }
}

/** Nullable variant for amounts that may be absent/null (e.g. fixed_amount of a variable charge). */
object FlexDoubleOrNull : KSerializer<Double?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexDoubleOrNull", PrimitiveKind.DOUBLE)
    @OptIn(ExperimentalSerializationApi::class)
    override fun serialize(encoder: Encoder, value: Double?) {
        if (value == null) encoder.encodeNull() else encoder.encodeDouble(value)
    }
    override fun deserialize(decoder: Decoder): Double? {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        if (el is JsonNull) return null
        val p = el as? JsonPrimitive ?: return null
        return p.doubleOrNull ?: p.content.trim().toDoubleOrNull()
    }
}

/** Some fields are numbers on one endpoint and text on another (e.g. billing_year). */
object FlexString : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexString", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
    override fun deserialize(decoder: Decoder): String {
        val el = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeString()
        if (el is JsonNull) return ""
        return (el as? JsonPrimitive)?.content ?: el.toString()
    }
}
