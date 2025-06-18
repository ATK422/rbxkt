package types.generator

import annotations.LuauName
import com.charleskorn.kaml.Yaml
import com.squareup.kotlinpoet.Annotatable
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.Documentable
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.Taggable
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.gradle.platform.base.TypeBuilder
import java.io.File
import kotlin.collections.map
import kotlin.reflect.KClass

private val json = Json { ignoreUnknownKeys = true }
private val yaml = Yaml(configuration = Yaml.default.configuration.copy(strictMode = false))

private val logger = System.getLogger("type gen")
private val client = HttpClient(CIO)

@Serializable
internal data class GithubFileTreeResponse(
    val payload: GithubFileTreePayload
)

@Serializable
internal data class GithubFileTreePayload(
    val tree: GithubTree
)

@Serializable
internal data class GithubTree(
    val totalCount: Int,
    val items: List<GithubTreeItem>,
)

@Serializable
internal data class GithubTreeItem(
    val name: String,
    val path: String,
    val contentType: String,
)

@Serializable
internal data class ClassSchema(
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

@Serializable
internal data class EnumFile(
    val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val items: List<EnumValue>
) {
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
    val security: String? = null,
    @SerialName("thread_safety")
    val threadSafety: String? = null,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
)

@Serializable
internal data class DataTypeFile(
    val name: String,
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
) {
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
internal data class ClassFile(
    val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val inherits: List<String>? = null,
    val properties: List<Property>? = null,
    val methods: List<Method>? = null,
    val events: List<ClassEvent>? = null,
//    val callbacks: List<ClassCallback> = null,
) {
    @Serializable
    internal data class ClassEvent(
        val name: String,
        val summary: String,
        val parameters: List<Parameter>,
        val tags: List<String>,
        val security: String,
        @SerialName("deprecation_message")
        val deprecationMessage: String,
    )
}

@Serializable
internal data class CorrectionsRoot(
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

internal data class CorrectionData(
    val parameters: Map<String, CorrectionDataParameter>? = null,
    val valueType: String? = null,
    val returnType: List<String>? = null
) {
    internal data class CorrectionDataParameter(
        val type: String? = null,
        val default: String? = null
    )
}

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

internal class RobloxTypeGenerator() {
    companion object {
        private const val BASE_GITHUB_URL = "https://github.com/Roblox/creator-docs/tree/main/content/en-us/reference/engine"
        private const val BASE_RAW_GITHUB_URL = "https://raw.githubusercontent.com/Roblox/creator-docs/refs/heads/main"
        private const val SCHEMA_RAW_GITHUB_URL = "$BASE_RAW_GITHUB_URL/tools/schemas/engine/classes.json"
        private const val CORRECTIONS_RAW_GITHUB_URL = "https://raw.githubusercontent.com/JohnnyMorganz/luau-lsp/refs/heads/main/scripts/Corrections.json"

        private lateinit var annotations: Map<String, AnnotationSpec>
    }

    private fun baseUrl(path: String): String = "$BASE_GITHUB_URL/$path"
    private fun baseRawUrl(path: String): String = "$BASE_RAW_GITHUB_URL/$path"

    private suspend fun listFiles(url: String): GithubTree {
        val response = client.get {
            url(url)
            headers {
                append("Accept", "application/json")
            }
        }
        // TODO: i love error handling
        val text = response.bodyAsText()
        val body = json.decodeFromString<GithubFileTreeResponse>(text)
        return body.payload.tree
    }

    private suspend fun readFiles(paths: GithubTree): List<String> = coroutineScope {
        paths.items.map { item ->
            async {
                client.get {
                    url(baseRawUrl(item.path))
                    headers {
                        append("Accept", "application/json")
                    }
                }.bodyAsText()
            }
        }.awaitAll()
    }

    private suspend fun readFile(rawPath: String): String = coroutineScope {
        async {
            client.get {
                url(rawPath)
                headers {
                    append("Accept", "application/json")
                }
            }.bodyAsText()
        }.await()
    }

    internal suspend fun generate() {
        val corrections = getCorrections()

        val annotationFileSpec = FileSpec.builder("xyz.atkdev.rbxkt.api", "RobloxAnnotations")
        val enumFileSpec = FileSpec.builder("xyz.atkdev.rbxkt.api", "RobloxEnums")
        val datatypeFileSpec = FileSpec.builder("xyz.atkdev.rbxkt.api", "RobloxDatatypes")
        val classFileSpec = FileSpec.builder("xyz.atkdev.rbxkt.api", "RobloxClasses")

        annotations = generateAnnotations(annotationFileSpec)
        generateEnums(enumFileSpec)
        generateDataTypes(datatypeFileSpec)
//        generateLuauGlobals()
//        generateRobloxGlobals()
//        generateLibraries()
        generateClasses(classFileSpec)

        annotationFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
        enumFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
        datatypeFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
    }

    private suspend fun getCorrections(): Map<String, Map<String, CorrectionData>> {
        val file = readFile(CORRECTIONS_RAW_GITHUB_URL)
        val corrections = json.decodeFromString<CorrectionsRoot>(file)
        return corrections.classes.associate { correction ->
            correction.name to correction.members.associate { member ->
                member.name to CorrectionData(
                    member.parameters?.associate { parameter ->
                        parameter.name to CorrectionData.CorrectionDataParameter(
                            parameter.type?.name,
                            parameter.default
                        )
                    },
                    member.valueType?.name,
                    if (member.returnType != null)
                        listOf(member.returnType.name ?: member.returnType.generic!!)
                    else
                        member.tupleReturn?.map { it.name!! }
                )
            }
        }
    }

    private suspend fun generateAnnotations(fileSpec: FileSpec.Builder): Map<String, AnnotationSpec> {
        val files = readFile(SCHEMA_RAW_GITHUB_URL)
        val schema = json.decodeFromString<ClassSchema>(files)
        val combinedNames = (schema.definitions.tags.items.enum + schema.definitions.threadSafety.enum + schema.definitions.securityTags.enum)
            .filterNot { it == "Deprecated" }
            .toMutableSet()

        combinedNames.forEach {
            fileSpec.addType(
                TypeSpec
                .annotationBuilder(it)
                .addModifiers(KModifier.INTERNAL)
                .build()
            )
        }

        return combinedNames.associateWith { AnnotationSpec.builder(ClassName("xyz.atkdev.rbxkt.api", it)).build() }
    }

    private suspend fun generateEnums(fileSpec: FileSpec.Builder): Map<String, TypeSpec> {
        val tree = listFiles(baseUrl("enums"))
        val files = readFiles(tree)

        return files.associate { yamlFile ->
            val file = yaml.decodeFromString<EnumFile>(yamlFile)
            val builder = TypeSpec.enumBuilder(file.name)

            addSummary(builder, file.summary)
            addDeprecation(builder, file.deprecationMessage)
            addTags(builder, file.tags)

            file.items.forEach { item ->
                val anonClassBuilder = TypeSpec.anonymousClassBuilder()

                addSummary(anonClassBuilder, item.summary)
                addDeprecation(anonClassBuilder, item.deprecationMessage)
                addTags(anonClassBuilder, item.tags)

                builder.addEnumConstant(item.name, anonClassBuilder.build())
            }

            val typeSpec = builder.build()
            fileSpec.addType(typeSpec)
            file.name to typeSpec
        }
    }

    private suspend fun generateDataTypes(fileSpec: FileSpec.Builder): Map<String, TypeSpec> {
        val tree = listFiles(baseUrl("datatypes"))
        val files = readFiles(tree)

        val dataTypeNames = files.map { yamlFile ->
            val file = yaml.decodeFromString<DataTypeFile>(yamlFile)
            file.name
        }

        return files.associate { yamlFile ->
            val file = yaml.decodeFromString<DataTypeFile>(yamlFile)
            val builder = TypeSpec.classBuilder(file.name)

            addSummary(builder, file.summary)
            addDeprecation(builder, file.deprecationMessage)
            addTags(builder, file.tags)

            val companionBuilder = TypeSpec.companionObjectBuilder()
            file.constructors?.forEach { constructor ->
                val funcName = constructor.name.substringAfter(".")
                val isDefaultBuilder = funcName == "new"
                val funcBuilder = if (isDefaultBuilder) FunSpec.constructorBuilder() else FunSpec.builder(funcName.toCamelCase())
                if (funcName != funcName.toCamelCase()) addLuauName(funcBuilder, funcName)
                if (!isDefaultBuilder) funcBuilder.addModifiers(KModifier.EXTERNAL)
                constructor.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }
                if (!isDefaultBuilder) funcBuilder.returns(ClassName("xyz.atkdev.rbxkt.api", file.name))

                addSummary(funcBuilder, constructor.summary)
                addDeprecation(funcBuilder, constructor.deprecationMessage)
                addTags(funcBuilder, constructor.tags)

                if (isDefaultBuilder) builder.primaryConstructor(funcBuilder.build()) else companionBuilder.addFunction(funcBuilder.build())
            }

            file.constants?.forEach { constant ->
                val propBuilder = PropertySpec.builder(constant.name.substringAfter("."), getBaseLuauType(constant.type))

                addSummary(propBuilder, constant.summary)
                addDeprecation(propBuilder, constant.deprecationMessage)
                addTags(propBuilder, constant.tags)

                propBuilder.initializer("TODO()")
                companionBuilder.addProperty(propBuilder.build())
            }

            file.properties?.let { builder.addProperties(it.map(::generateProperty)) }

            file.methods?.let { methods -> builder.addFunctions(methods.flatMap { generateMethod(file.name, it, companionBuilder) }) }

            file.mathOperations?.forEach { mathOperation ->
                val operation = operatorToString(mathOperation.operation)
                val mathBuilder = FunSpec.builder(operation)
                    .addModifiers(KModifier.EXTERNAL)
                    .receiver(ClassName("xyz.atkdev.rbxkt.api", mathOperation.typeA))
                    .addParameter("other", getBaseLuauType(mathOperation.typeB))
                    .returns(ClassName("xyz.atkdev.rbxkt.api", mathOperation.returnType))

                if (operation != "floorDiv") mathBuilder.addModifiers(KModifier.OPERATOR)

                builder.addFunction(mathBuilder.build())
            }

            if (companionBuilder.propertySpecs.isNotEmpty() || companionBuilder.funSpecs.isNotEmpty() || companionBuilder.typeSpecs.isNotEmpty()) builder.addType(companionBuilder.build())
            val dataType = builder.build()
            fileSpec.addType(dataType)

            file.name to dataType
        }
    }

    private suspend fun generateLuauGlobals() {}
    private suspend fun generateRobloxGlobals() {}
    private suspend fun generateLibraries() {}

    private suspend fun generateClasses(fileSpec: FileSpec.Builder) {
        val tree = listFiles(baseUrl("classes"))
        val files = readFiles(tree)

        files.forEach { yamlFile ->
            val file = yaml.decodeFromString<ClassFile>(yamlFile)
            val interfaceBuilder = TypeSpec.interfaceBuilder("I${file.name}")

            addSummary(interfaceBuilder, file.summary)
            addDeprecation(interfaceBuilder, file.deprecationMessage)
            addTags(interfaceBuilder, file.tags)

            file.properties?.let {
                interfaceBuilder.addProperties(it.map(::generateProperty))
            }

        }
    }

    private fun addSummary(builder: Documentable.Builder<*>, summary: String) {
        val modified = summary
            .replace(Regex("(<.*?>)"), "")
            .replace("**", "__")
            .replace("\"", "\\\"")
            .replace("../../../", BASE_GITHUB_URL.substringBefore("/reference/engine"))
        if (modified.isNotEmpty()) builder.addKdoc("%L", modified)
    }

    private fun addDeprecation(builder: Annotatable.Builder<*>, deprecationMessage: String) {
        if (deprecationMessage.isNotBlank())
            builder
                .addAnnotation(
                    AnnotationSpec
                        .builder(Deprecated::class)
                        .addMember("\"${deprecationMessage
                            .replace("\n", " ")
                            .replace("\\","\\\"")
                            .replace("\"", "\\\"")}\""
                        )
                        .build())
    }

    private fun addTags(builder: Annotatable.Builder<*>, tags: List<String>) {
        builder.addAnnotations(
            tags
            .filterNot { it == "Deprecated" }
            .map { annotations[it] ?: error("Annotation $it not found!") }
        )
    }

    private fun addLuauName(builder: Annotatable.Builder<*>, name: String) {
        builder.addAnnotation(
            AnnotationSpec.builder(LuauName::class)
                .addMember("\"%L\"", name).build())
    }

    private fun generateReturnClass(
        funcName: String,
        returnList: List<Return>
    ): TypeSpec {
        val classBuilder = TypeSpec.classBuilder("${funcName}Return")
            .addAnnotation(ClassName("kotlin", "ConsistentCopyVisibility"))
            .addModifiers(KModifier.DATA)

        val constructor = FunSpec.constructorBuilder()
            .addModifiers(KModifier.PRIVATE)

        returnList.forEachIndexed { index, ret ->
            val name = "a$index"
            val type = getBaseLuauType(ret.type)

            val param = ParameterSpec.builder(name, type)
            addSummary(param, ret.summary)
            constructor.addParameter(param.build())

            val prop = PropertySpec.builder(name, type)
                .initializer(name)
            addSummary(prop, ret.summary)
            classBuilder.addProperty(prop.build())
        }

        return classBuilder.primaryConstructor(constructor.build()).build()
    }

    private fun generateProperty(property: Property): PropertySpec {
        val propName = property.name.substringAfter(".")
        val type = getBaseLuauType(property.type)
        val propBuilder = PropertySpec.builder(propName.toCamelCase(), type).mutable(true)

        if (propName != propName.toCamelCase()) addLuauName(propBuilder, propName)

        if (type == Boolean::class.asClassName())
            propBuilder.initializer("false")
        else if (type.isNullable)
            propBuilder.initializer("null")
        else
            propBuilder.addModifiers(KModifier.LATEINIT)

        addSummary(propBuilder, property.summary)
        addDeprecation(propBuilder, property.deprecationMessage)
        addTags(propBuilder, property.tags + listOfNotNull(property.security, property.threadSafety))

        return propBuilder.build()
    }

    private fun generateMethod(className: String, method: Method, companion: TypeSpec.Builder): List<FunSpec> {
        if (method.parameters?.any { it.type.contains(" | ") || it.type.contains(" & ") } == true) {
            val methods = mutableListOf<FunSpec>()

            val paramVariants: List<List<Parameter>> =
                method.parameters.map { p ->
                    when {
                        p.type.contains(" | ") -> p.type.split(" | ")
                            .map { t -> p.copy(type = t.trim()) }

                        p.type.contains(" & ") -> p.type.split(" & ")
                            .map { t -> p.copy(type = t.trim()) }

                        else -> listOf(p)
                    }
                }

            for (variantParams in cartesianProduct(paramVariants)) {
                val overload = method.copy(parameters = variantParams)
                methods += generateMethod(className, overload, companion)
            }

            return methods
        }

        val methodName = method.name.substringAfter(":")
        val funcBuilder = FunSpec.builder(methodName.toCamelCase())
        if (methodName != methodName.toCamelCase()) addLuauName(funcBuilder, methodName)
        funcBuilder.addModifiers(KModifier.EXTERNAL)

        method.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }

        if (method.returns.size > 1) {
            val returnClass = generateReturnClass(methodName, method.returns)
            companion.addType(returnClass)
            funcBuilder.returns(
                ClassName("xyz.atkdev.rbxkt.api", className)
                    .nestedClass("Companion")
                    .nestedClass(returnClass.name!!))
        } else if (method.returns.size == 1 && method.returns[0].type != "()") {
            funcBuilder.returns(getBaseLuauType(method.returns[0].type))
        }

        addSummary(funcBuilder, method.summary)
        addDeprecation(funcBuilder, method.deprecationMessage)
        addTags(funcBuilder, method.tags)

        return listOf(funcBuilder.build())
    }

    private fun generateParameter(parameter: Parameter): ParameterSpec {
        var cleanName = parameter.name.replace("...", "")
        if (cleanName.isEmpty()) cleanName = "a0"
        val builder = ParameterSpec.builder(cleanName.toCamelCase(), getBaseLuauType(parameter.type, parameter.default == "nil"))
        if ((parameter.default != null && parameter.default.isNotEmpty()) || parameter.type.contains("?")) {
            var cleanDefault = (parameter.default ?: "null")
                .replace("nil", "null")
                .replace("Enum.", "")
                .replace(".new", "")

            cleanDefault = Regex("\\s(?=(?:[^()]*\\([^()]*\\))*[^()]*$)").split(cleanDefault, 2).first()
            builder.defaultValue(cleanDefault)
        }
        if (parameter.name.contains("...")) builder.addModifiers(KModifier.VARARG)
        return builder.build()
    }

    private fun getBaseLuauType(type: String, forceNullable: Boolean = false): TypeName {
        val isNullable = forceNullable || type.contains("?") || type.contains("| nil")
        val containsGenerics = type.contains("<")

        val cleanName = type
            .replace("?", "")
            .replace("Enum.", "")
            .replace(Regex("(<.*?>)"), "")
            .replace(" | nil", "")

        var typeName: TypeName = when (cleanName) {
            "number" -> Number::class.asClassName()
            "string" -> String::class.asClassName()
            "Array" -> Array::class.asClassName()
            "Object" -> Any::class.asClassName()
            "bool", "boolean" -> Boolean::class.asClassName()
            "Tuple" -> Any::class.asClassName()
            "table" -> Map::class.asClassName()
            "Variant" -> Any::class.asClassName()
            "Dictionary" -> Map::class.asClassName()
            "function" -> Function::class.asClassName()
            else -> ClassName("xyz.atkdev.rbxkt.api", cleanName)
        }

        if (cleanName == "Array" && !containsGenerics || cleanName == "function") typeName = (typeName as ClassName).parameterizedBy(STAR)
        else if (cleanName == "table" || cleanName == "Dictionary") typeName = (typeName as ClassName).parameterizedBy(STAR, STAR)
        else if (cleanName == "Tuple" && containsGenerics) typeName = ClassName("xyz.atkdev.rbxkt.api", type.substringAfter("<").substringBeforeLast(">"))
        else {
            if (containsGenerics) {
                val generics = type.substringAfter("<").substringBeforeLast(">").split(",")
                typeName = (typeName as ClassName).parameterizedBy(generics.map { getBaseLuauType(it) })
            }
        }

        return typeName.copy(isNullable)
    }

    private fun String.toCamelCase(): String = this.first().lowercase() + this.substring(1)

    private fun <T> cartesianProduct(sets: List<List<T>>): List<List<T>> =
        sets.fold(listOf(emptyList())) { acc, set ->
            acc.flatMap { prefix -> set.map { prefix + it } }
        }

    private fun operatorToString(operator: String): String = when (operator) {
        "+" -> "plus"
        "-" -> "minus"
        "*" -> "times"
        "/" -> "div"
        "%" -> "rem"
        "//" -> "floorDiv"
        else -> error("$operator is not an operator")
    }
}