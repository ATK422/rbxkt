package types.utils

import annotations.LuauName
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.TypeSpec
import types.fetch.GithubApi.BASE_GITHUB_URL
import types.generator.RobloxTypeGenerator

internal fun <B : Documentable.Builder<B>> B.addSummary(summary: String) = apply {
    val modified = summary
        .replace(Regex("(<.*?>)"), "")
        .replace("**", "__")
        .replace("\"", "\\\"")
        .replace("../../../", BASE_GITHUB_URL.substringBefore("/reference/engine"))
    if (modified.isNotEmpty()) addKdoc("%L", modified)
}

internal fun <B : Annotatable.Builder<B>> B.addDeprecation(deprecationMessage: String) = apply {
    if (deprecationMessage.isNotBlank())
        addAnnotation(
            AnnotationSpec
                .builder(Deprecated::class)
                .addMember("\"${deprecationMessage
                    .replace("\n", " ")
                    .replace("\\","\\\"")
                    .replace("\"", "\\\"")}\""
                )
                .build()
        )
}

context(generator: RobloxTypeGenerator)
internal fun <B : Annotatable.Builder<B>> B.addTags(tags: List<String>) = apply {
    addAnnotations(
        tags
            .filterNot { it == "Deprecated" }
            .map { generator.annotations[it] ?: error("Annotation $it not found!") }
    )
}

internal fun <B : Annotatable.Builder<B>> B.addLuauName(name: String) = apply {
    addAnnotation(
        AnnotationSpec.builder(LuauName::class)
            .addMember("\"%L\"", name).build()
    )
}

private inline fun <T, K> List<T>.dedupeBy(
    crossinline keySelector: (T) -> K,
    crossinline isDeprecated: (T) -> Boolean
): List<T> = withIndex()
    .groupBy { keySelector(it.value) }
    .values
    .map { group ->
        // if there’s more than one candidate, drop the deprecated ones (if any),
        // otherwise fall back to the full group
        val keep = if (group.size > 1) {
            group.filterNot { isDeprecated(it.value) }
                .ifEmpty { group }
        } else group
        // pick the one with the lowest original index
        keep.minBy { it.index }.value
    }

internal fun TypeSpec.Builder.dedupeFunSpecs(): Unit = funSpecs.run {
    val newSpecs = dedupeBy(
        { fs -> fs.name to fs.parameters.map { it.type } },
        { fs ->
            fs.annotations.any { it.typeName == ClassName("kotlin", "Deprecated") }
        }
    )
    clear()
    addAll(newSpecs)
}

internal fun TypeSpec.Builder.dedupePropertySpecs(): Unit = propertySpecs.run {
    val newSpecs = dedupeBy(
        { prop -> prop.name },
        { prop ->
            prop.annotations.any { it.typeName == ClassName("kotlin", "Deprecated") }
        }
    )
    clear()
    addAll(newSpecs)
}

