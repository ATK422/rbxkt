package com.rbxkt.typegen.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class CorrectionsModel(
    @SerialName("Classes")
    val classes: List<Correction>,
) {
    @Serializable
    internal data class Correction(
        @SerialName("Name")
        val name: String,
        @SerialName("Members")
        val members: List<CorrectionMember>,
    ) {
        @Serializable
        internal data class CorrectionMember(
            @SerialName("Name")
            val name: String,
            @SerialName("ReturnType")
            val returnType: CorrectionType? = null,
            @SerialName("TupleReturns")
            val tupleReturn: List<CorrectionType>? = null,
            @SerialName("ValueType")
            val valueType: CorrectionType? = null,
            @SerialName("Parameters")
            val parameters: List<CorrectionParameter>? = null
        ) {
            @Serializable
            internal data class CorrectionParameter(
                @SerialName("Name")
                val name: String,
                @SerialName("Type")
                val type: CorrectionType? = null,
                @SerialName("Default")
                val default: String? = null
            )

            @Serializable
            internal data class CorrectionType(
                @SerialName("Name")
                val name: String? = null,
                @SerialName("Generic")
                val generic: String? = null,
            )
        }
    }
}