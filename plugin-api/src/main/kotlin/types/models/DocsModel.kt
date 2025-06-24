package types.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import types.generator.DocsModel
import types.serializers.AlwaysStringSerializer

@Serializable
internal data class EnumModel(
    override val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val items: List<EnumValue>
): DocsModel {
    @Serializable
    internal data class EnumValue(
        val name: String,
        val summary: String,
        val tags: List<String>,
        @SerialName("deprecation_message")
        val deprecationMessage: String
    )
}

@Serializable
internal data class Parameter(
    val name: String,
    val type: String,
    @Serializable(with = AlwaysStringSerializer::class)
    val default: String? = null,
    val summary: String,
)

@Serializable
internal data class Return(
    val type: String,
    val summary: String,
)

@Serializable
internal data class Method(
    val name: String,
    val summary: String,
    val parameters: List<Parameter>? = emptyList(),
    val returns: List<Return>,
    val tags: List<String>,
    @SerialName("thread_safety")
    val threadSafety: String? = null,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
)

@Serializable
internal data class Property(
    val name: String,
    val type: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("thread_safety")
    val threadSafety: String? = null,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
)

@Serializable
internal data class DatatypeModel(
    override val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val constructors: List<DataTypeConstructor>? = listOf(),
    val constants: List<DataTypeConstant>? = listOf(),
    val properties: List<Property>? = listOf(),
    val methods: List<Method>? = null,
    @SerialName("math_operations")
    val mathOperations: List<DataTypeMathOperation>? = null,
): DocsModel {
    @Serializable
    internal data class DataTypeConstructor(
        val name: String,
        val summary: String,
        val tags: List<String>,
        val parameters: List<Parameter>? = emptyList(),
        @SerialName("deprecation_message")
        val deprecationMessage: String,
    )

    @Serializable
    internal data class DataTypeConstant(
        val name: String,
        val type: String,
        val summary: String,
        val tags: List<String>,
        @SerialName("deprecation_message")
        val deprecationMessage: String,
    )

    @Serializable
    internal data class DataTypeMathOperation(
        val operation: String,
        val summary: String,
        @SerialName("type_a")
        val typeA: String,
        @SerialName("type_b")
        val typeB: String,
        @SerialName("return_type")
        val returnType: String,
        val tags: List<String>,
        @SerialName("deprecation_message")
        val deprecationMessage: String,
    )
}

@Serializable
internal data class ClassModel(
    override val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val inherits: List<String>? = null,
    val properties: List<Property>? = null,
    val methods: List<Method>? = null,
    val events: List<ClassEvent>? = null,
//    val callbacks: List<ClassCallback> = null,
): DocsModel {
    @Serializable
    internal data class ClassEvent(
        val name: String,
        val summary: String,
        val parameters: List<Parameter>,
        val tags: List<String>,
        @SerialName("deprecation_message")
        val deprecationMessage: String,
    )
}