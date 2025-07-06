package com.rbxkt.typegen.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object AlwaysStringSerializer : KSerializer<String> {
    override val descriptor = PrimitiveSerialDescriptor("AlwaysString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String = when (decoder) {
        is JsonDecoder -> {
            val element = decoder.decodeJsonElement()
            when (element) {
                is JsonPrimitive -> if (element.isString) element.content else element.toString()
                else -> element.toString()
            }
        }

        else -> decoder.decodeString()
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}
