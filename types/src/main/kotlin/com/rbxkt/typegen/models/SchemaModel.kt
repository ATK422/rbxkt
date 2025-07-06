package com.rbxkt.typegen.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class SchemaModel(
    val definitions: SchemaDefinitions,
) {
    @Serializable
    internal data class SchemaDefinitions(
        @SerialName("security_tags")
        val securityTags: SchemaTagsEnum,
        @SerialName("thread_safety")
        val threadSafety: SchemaTagsEnum,
        val tags: SchemaTags,
    ) {
        @Serializable
        internal data class SchemaTags(val items: SchemaTagsEnum)

        @Serializable
        internal data class SchemaTagsEnum(val enum: List<String>)
    }
}