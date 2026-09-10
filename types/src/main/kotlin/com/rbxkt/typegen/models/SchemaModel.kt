package com.rbxkt.typegen.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SchemaModel(
    val definitions: Map<String, SchemaDefinition>,
) {
    @Serializable
    internal data class SchemaDefinition(
        @SerialName("\$id") val id: String? = null,
        val enum: List<String>? = null,
        val items: SchemaDefinition? = null,
    )

    fun annotationNames(): Set<String> {
        fun definition(vararg names: String): SchemaDefinition =
            definitions.values.firstOrNull { it.id in names }
                ?: names.firstNotNullOfOrNull { definitions[it] }
                ?: error("Creator docs schema is missing ${names.joinToString("/")}")

        val tags = definition("tags").items?.enum ?: error("Creator docs tags schema has no enum")
        val security = definition("security_tag", "security_tags").enum
            ?: error("Creator docs security schema has no enum")
        val threadSafety = definition("thread_safety").enum
            ?: error("Creator docs thread safety schema has no enum")
        return (tags + threadSafety + security).filterNot { it == "Deprecated" }.toSet()
    }
}
