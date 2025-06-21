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
import com.squareup.kotlinpoet.LambdaTypeName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.UNIT
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.io.File
import kotlin.Deprecated
import kotlin.collections.ifEmpty
import kotlin.collections.map

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

internal interface DocsFile {
    val name: String
}

@Serializable
internal data class EnumFile(
    override val name: String,
    val summary: String,
    val tags: List<String>,
    @SerialName("deprecation_message")
    val deprecationMessage: String,
    val items: List<EnumValue>
): DocsFile {
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
internal data class DataTypeFile(
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
): DocsFile {
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
): DocsFile {
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
        
        private const val PACKAGE_NAME = "xyz.atkdev.rbxkt.api"

        private lateinit var annotations: Map<String, AnnotationSpec>
        private lateinit var enums: Map<String, ClassName>
        private lateinit var datatypes: Map<String, ClassName>
        private lateinit var classes: Map<String, ClassName>
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
    
    private suspend fun listAndReadFiles(url: String) = readFiles(listFiles(url))

    private inline fun <reified T : DocsFile> processYamlFiles(files: List<String>): Map<String, T> {
        return files.associate {
            val file = yaml.decodeFromString<T>(it)
            file.name to file
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun generate() {
        val corrections = getCorrections()

        val suppressAnnotation = AnnotationSpec.builder(Suppress::class)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .addMember("%L", "\"unused\", \"unused_parameter\", \"RedundantVisibilityModifier\", \"RemoveRedundantQualifierName\", \"SpellCheckingInspection\", \"DEPRECATION\"")
            .build()

        val annotationFileSpec = FileSpec.builder(PACKAGE_NAME, "RobloxAnnotations").addAnnotation(suppressAnnotation)
        val enumFileSpec = FileSpec.builder("$PACKAGE_NAME.enums", "RobloxEnums").addAnnotation(suppressAnnotation)
        val datatypeFileSpec = FileSpec.builder("$PACKAGE_NAME.datatypes", "RobloxDatatypes").addAnnotation(suppressAnnotation)
        val classFileSpec = FileSpec.builder("$PACKAGE_NAME.classes", "RobloxClasses").addAnnotation(suppressAnnotation)

        val (enumFiles, datatypeFiles, classFiles) = coroutineScope {
            val enums = async { processYamlFiles<EnumFile>(listAndReadFiles(baseUrl("enums"))) }
            val datatypes = async { processYamlFiles<DataTypeFile>(listAndReadFiles(baseUrl("datatypes"))) }
            val classes = async { processYamlFiles<ClassFile>(listAndReadFiles(baseUrl("classes"))) }

            awaitAll(enums, datatypes, classes)

            Triple(enums.getCompleted(), datatypes.getCompleted(), classes.getCompleted())
        }


        enums = enumFiles.keys.associateWith { ClassName("$PACKAGE_NAME.enums", it) }
        datatypes = datatypeFiles.keys.associateWith { ClassName("$PACKAGE_NAME.datatypes", it) }
        classes = classFiles.keys.associateWith { ClassName("$PACKAGE_NAME.classes", it) }

        annotations = generateAnnotations(annotationFileSpec)

        coroutineScope {
            launch { generateEnums(enumFiles, enumFileSpec) }
            launch { generateDataTypes(datatypeFiles, datatypeFileSpec) }
            launch { generateClasses(classFiles, classFileSpec) }
        }

        annotationFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
        enumFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
        datatypeFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
        classFileSpec.build().writeTo(File(System.getProperty("user.dir")+"/src/api/"))
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

        return combinedNames.associateWith { AnnotationSpec.builder(ClassName(PACKAGE_NAME, it)).build() }
    }

    private fun generateEnums(files: Map<String, EnumFile>, fileSpec: FileSpec.Builder) {
        files.forEach { (name, file) ->
            val builder = TypeSpec.enumBuilder(name)

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
        }
    }

    private suspend fun generateDataTypes(files: Map<String, DataTypeFile>, fileSpec: FileSpec.Builder) {
        files.forEach { (name, file) ->
            val builder = TypeSpec.classBuilder(name)

            addSummary(builder, file.summary)
            addDeprecation(builder, file.deprecationMessage)
            addTags(builder, file.tags)

            var hasPrimaryConstructor = false
            val companionBuilder = TypeSpec.companionObjectBuilder()
            file.constructors?.forEach { constructor ->
                val funcName = constructor.name.substringAfter(".")
                val isDefaultBuilder = funcName == "new"
                val funcBuilder = if (isDefaultBuilder) FunSpec.constructorBuilder() else FunSpec.builder(funcName.toCamelCase())
                if (funcName != funcName.toCamelCase()) addLuauName(funcBuilder, funcName)
                if (!isDefaultBuilder) funcBuilder.addModifiers(KModifier.EXTERNAL)
                constructor.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }
                if (!isDefaultBuilder) funcBuilder.returns(datatypes[name]!!)

                addSummary(funcBuilder, constructor.summary)
                addDeprecation(funcBuilder, constructor.deprecationMessage)
                addTags(funcBuilder, constructor.tags)

                hasPrimaryConstructor = isDefaultBuilder

                if (isDefaultBuilder) builder.primaryConstructor(funcBuilder.build()) else companionBuilder.addFunction(funcBuilder.build())
            }

            file.constants?.forEach { constant ->
                val propBuilder = PropertySpec.builder(constant.name.substringAfter("."), typeOf(constant.type))

                addSummary(propBuilder, constant.summary)
                addDeprecation(propBuilder, constant.deprecationMessage)
                addTags(propBuilder, constant.tags)

                propBuilder.initializer("TODO()")
                companionBuilder.addProperty(propBuilder.build())
            }

            val constantNames = file.constants?.map { it.name.substringAfter(".") } ?: emptyList()
            file.properties?.let { property ->
                builder
                    .addProperties(property
                        .filterNot { it.name in constantNames }
                        .map(::generateProperty))
            }

            file.methods?.let { methods -> builder.addFunctions(methods.flatMap { generateMethod(datatypes[name]!!, it, companionBuilder) }) }

            file.mathOperations?.forEach { mathOperation ->
                val operation = operatorToString(mathOperation.operation)
                val mathBuilder = FunSpec.builder(operation)
                    .addModifiers(KModifier.EXTERNAL)
                    .addParameter("other", typeOf(mathOperation.typeB))
                    .returns(typeOf(mathOperation.returnType))

                if (operation != "floorDiv") mathBuilder.addModifiers(KModifier.OPERATOR)

                builder.addFunction(mathBuilder.build())
            }

            if (name.contains("Params")) {
                builder.addFunction(
                    FunSpec.constructorBuilder()
                        .addParameter("builder", LambdaTypeName.get(
                            datatypes[name]!!,
                            emptyList(),
                            UNIT)
                        )
                        .callThisConstructor()
                        .build()
                )
                if (!hasPrimaryConstructor) builder.primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
            }

            if (companionBuilder.propertySpecs.isNotEmpty() || companionBuilder.funSpecs.isNotEmpty() || companionBuilder.typeSpecs.isNotEmpty()) builder.addType(companionBuilder.build())
            val dataType = builder.build()
            fileSpec.addType(dataType)
        }
    }

    private suspend fun generateLuauGlobals() {}
    private suspend fun generateRobloxGlobals() {}
    private suspend fun generateLibraries() {}

    private suspend fun generateClasses(files: Map<String, ClassFile>, fileSpec: FileSpec.Builder) {
        val interfaces = files.mapValues { (name, file) ->
            if (name == "Studio") return@mapValues TypeSpec.objectBuilder("Studio").build()
            val interfaceBuilder = TypeSpec.interfaceBuilder("I${name}")

            val companionBuilder = TypeSpec.companionObjectBuilder()

            addDeprecation(interfaceBuilder, file.deprecationMessage)
            addTags(interfaceBuilder, file.tags)

            file.properties?.let { prop -> interfaceBuilder.addProperties(prop.map { generateProperty(it, false) } ) }

            file.methods?.let { methods -> interfaceBuilder.addFunctions(methods.flatMap { generateMethod(classes[name]!!, it, companionBuilder, false) }) }

            file.events?.forEach { event ->
                val eventName = event.name.substringAfter(".")
                val propertyBuilder = PropertySpec.builder(eventName.toCamelCase(), datatypes["RBXScriptConnection"]!!)
                if (eventName != eventName.toCamelCase()) addLuauName(propertyBuilder, eventName)
                if (eventName == "Changed" && name != "Object") propertyBuilder.addModifiers(KModifier.OVERRIDE)
                addSummary(propertyBuilder, event.summary)
                addDeprecation(propertyBuilder, event.deprecationMessage)
                addTags(propertyBuilder, event.tags)
                interfaceBuilder.addProperty(propertyBuilder.build())
            }

            file.inherits?.forEach {
                interfaceBuilder.addSuperinterface(ClassName("$PACKAGE_NAME.classes", "I${it}"))
            }

            if (companionBuilder.propertySpecs.isNotEmpty() || companionBuilder.funSpecs.isNotEmpty() || companionBuilder.typeSpecs.isNotEmpty()) interfaceBuilder.addType(companionBuilder.build())

            if (name == "Instance") {
                interfaceBuilder.addFunction(
                    FunSpec.builder("get")
                        .addParameter("name", String::class)
                        .addModifiers(KModifier.OPERATOR, KModifier.ABSTRACT)
                        .returns(classes["Instance"]!!.copy(nullable = true))
                        .build()
                )
            }

            interfaceBuilder.funSpecs.run {
                val funSpecs = removeDuplicatesBySignature()
                clear()
                addAll(funSpecs)
            }

            interfaceBuilder.propertySpecs.run {
                val propSpecs = removeDuplicatesByName()
                clear()
                addAll(propSpecs)
            }

            return@mapValues interfaceBuilder.build()
        }

        //Can move this outside if needed and just pass in interfaces
        fun getInterfaces(name: String): Set<String> {
            val iface = interfaces[name] ?: error("Roblox interfaces are wrong! Pray you dont see this error: $name")
            val otherNames = iface
                .superinterfaces
                .keys
                .map { (it as ClassName).simpleName.replaceFirst("I", "") }
                .filterNot { it == "Instance" }
                .map { getInterfaces(it) }
                .flatten()
            return setOf(iface.name!!.replaceFirst("I", "")) + otherNames
        }

        files.forEach { (name, file) ->
            if (name == "Studio") return@forEach
            val builder = if (file.tags.contains("Service")) TypeSpec.objectBuilder(name) else TypeSpec.classBuilder(name)

            builder.addSuperinterface(ClassName("$PACKAGE_NAME.classes", "I${name}"))
            if (name != "Instance") builder.superclass(classes["Instance"]!!)
            else builder.addModifiers(KModifier.OPEN)

            getInterfaces(name).forEach { ifaceName ->
                val iface = interfaces[ifaceName]!!

                iface.propertySpecs.forEach { prop ->
                    val propSpec = prop.toBuilder()
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("TODO()")
                    propSpec.annotations.removeIf { it.typeName == LuauName::class.asTypeName() }
                    propSpec.kdoc.clear()
                    builder.addProperty(propSpec.build())
                }

                iface.funSpecs.forEach { func ->
                    val funcSpec = func.toBuilder()
                        .addModifiers(KModifier.OVERRIDE, KModifier.EXTERNAL)
                    funcSpec.parameters.run {
                        val clean = map {
                            it.toBuilder().defaultValue(null).build()
                        }
                        clear()
                        addAll(clean)
                    }
                    funcSpec.modifiers.remove(KModifier.ABSTRACT)
                    funcSpec.annotations.removeIf { it.typeName == LuauName::class.asTypeName() }
                    funcSpec.kdoc.clear()
                    builder.addFunction(funcSpec.build())
                }
            }

            if (!file.tags.contains("Service")) {
                builder.addFunction(
                    FunSpec.constructorBuilder()
                        .addParameter("builder", LambdaTypeName.get(
                            classes[name]!!,
                            emptyList(),
                            UNIT)
                        )
                        .callThisConstructor()
                        .build()
                )
            }

            if (!file.tags.contains("Service")) {
                if (!file.tags.contains("NotCreatable") || name == "Instance")
                    builder.primaryConstructor(FunSpec.constructorBuilder().build())
                else
                    builder.primaryConstructor(FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build())
            }
            addSummary(builder, file.summary)

            builder.funSpecs.run {
                val funSpecs = removeDuplicatesBySignature()
                clear()
                addAll(funSpecs)
            }

            builder.propertySpecs.run {
                val propSpecs = removeDuplicatesByName()
                clear()
                addAll(propSpecs)
            }

            val typeSpec = builder.build()
            fileSpec.addType(typeSpec)
        }

        //goofy stuff, but it makes the code much easier to work with
        interfaces.forEach { (_, iface) ->
            val newInterface = iface.toBuilder()

            newInterface.annotations.removeIf { it.typeName != LuauName::class.asTypeName() }

            val newProperties = newInterface.propertySpecs.map { prop ->
                val builder = prop.toBuilder()
                builder.annotations.removeIf { it.typeName != LuauName::class.asTypeName() }
                return@map builder.build()
            }
            newInterface.propertySpecs.clear()
            newInterface.addProperties(newProperties)

            val newFunctions = newInterface.funSpecs.map { func ->
                val builder = func.toBuilder()
                builder.annotations.removeIf { it.typeName != LuauName::class.asTypeName() }
                return@map builder.build()
            }
            newInterface.funSpecs.clear()
            newInterface.addFunctions(newFunctions)

            fileSpec.addType(newInterface.build())
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
            val type = typeOf(ret.type)

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

    private fun generateEvent(event: ClassFile.ClassEvent, initialize: Boolean = true): PropertySpec {
        val propertyBuilder = PropertySpec.builder(event.name.substringAfter("."), ClassName(PACKAGE_NAME, "RBXScriptConnection"))
        if (initialize) propertyBuilder.initializer("TODO()")

        addSummary(propertyBuilder, event.summary)
        addDeprecation(propertyBuilder, event.deprecationMessage)
        addTags(propertyBuilder, event.tags)

        return propertyBuilder.build()
    }

    private fun generateProperty(property: Property, initialize: Boolean = true): PropertySpec {
        val propName = property.name.substringAfter(".")
        val type = typeOf(property.type)
        val propBuilder = PropertySpec.builder(propName.toCamelCase(), type).mutable(true)

        if (propName != propName.toCamelCase()) addLuauName(propBuilder, propName)

        if (initialize) {
            if (type == Boolean::class.asClassName())
                propBuilder.initializer("false")
            else if (type.isNullable)
                propBuilder.initializer("null")
            else
                propBuilder.addModifiers(KModifier.LATEINIT)
        }

        addSummary(propBuilder, property.summary)
        addDeprecation(propBuilder, property.deprecationMessage)
        addTags(propBuilder, property.tags + listOfNotNull(property.threadSafety))

        return propBuilder.build()
    }

    private fun generateMethod(className: ClassName, method: Method, companion: TypeSpec.Builder, external: Boolean = true): List<FunSpec> {
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
        if (external) funcBuilder.addModifiers(KModifier.EXTERNAL) else funcBuilder.addModifiers(KModifier.ABSTRACT)

        method.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }

        if (method.returns.size > 1) {
            val returnClass = generateReturnClass(methodName, method.returns)
            companion.addType(returnClass)
            funcBuilder.returns(
                className
                    .nestedClass("Companion")
                    .nestedClass(returnClass.name!!))
        } else if (method.returns.size == 1 && method.returns[0].type != "()") {
            funcBuilder.returns(typeOf(method.returns[0].type))
        }

        addSummary(funcBuilder, method.summary)
        addDeprecation(funcBuilder, method.deprecationMessage)
        addTags(funcBuilder, method.tags)

        return listOf(funcBuilder.build())
    }

    private fun generateParameter(parameter: Parameter): ParameterSpec {
        var cleanName = parameter.name.replace("...", "")
        if (cleanName.isEmpty()) cleanName = "a0"
        val type = typeOf(parameter.type, parameter.default == "nil" || parameter.default == "{}")
        val builder = ParameterSpec.builder(cleanName.toCamelCase(), type)
        if ((parameter.default != null && parameter.default.isNotEmpty()) || parameter.type.contains("?")) {
            var cleanDefault = (parameter.default?.takeIf { it.isNotEmpty() } ?: "null")
                .replace("{}", "null")
                .replace("nil", "null")
                .replace("Enum.", "")
                .replace(".new", "")
                .replace("{", "(")
                .replace("}", ")")
                .replace("()", "{}")

            cleanDefault = if (type == String::class.asClassName()) "\"$cleanDefault\""
            else if (type == datatypes["Vector3"] && !cleanDefault.contains("Vector3")) "Vector3($cleanDefault)"
            else cleanDefault.replace(Regex("\\(([^)]*)\\)"), "()")

            cleanDefault = Regex("\\s(?=(?:[^()]*\\([^()]*\\))*[^()]*$)").split(cleanDefault, 2).first()

            if (cleanDefault.contains("="))
                cleanDefault = Regex("\\(([^)]*)\\)").replace(cleanDefault) { match ->
                    val params = match.groupValues[1]
                        .split(Regex("\\s*,\\s*"))
                        .joinToString(", ") { param ->
                            val split = param.split("=", limit = 2)
                            val name = split[0].toCamelCase()
                            val value = split.getOrElse(1) { return@joinToString name }

                            "${name.trim().toCamelCase()}=${value.trim()}"
                        }
                    "($params)"
                }

            val enum = (type as? ClassName)?.simpleName?.let { if (!cleanDefault.contains(".")) enums[it] else null }
            builder.defaultValue(enum?.nestedClass(cleanDefault)?.toString() ?: cleanDefault)
        }
        if (parameter.name.contains("...")) builder.addModifiers(KModifier.VARARG)
        return builder.build()
    }

    private fun typeOf(type: String, forceNullable: Boolean = false): TypeName {
        val isNullable = forceNullable || type.contains("?") || type.contains("| nil")
        val containsGenerics = type.contains("<")

        val cleanName = type
            .replace("?", "")
            .replace("Enum.", "")
            .replace(Regex("(<.*?>)"), "")
            .replace(" | nil", "")

        var typeName: TypeName = when (cleanName) {
            "number", "int64", "int", "float", "double" -> Number::class.asClassName()
            "string", "ContentId", "AdReward" -> String::class.asClassName()
            "Array" -> Array::class.asClassName()
            "bool", "boolean" -> Boolean::class.asClassName()
            "table" -> Map::class.asClassName()
            "Dictionary" -> Map::class.asClassName()
            "function", "Function" -> Function::class.asClassName()
            else -> enums[cleanName] ?: classes[cleanName] ?: datatypes[cleanName] ?: Any::class.asClassName()
        }

        if (cleanName == "Array" && !containsGenerics || cleanName == "function" || cleanName == "Function") typeName = (typeName as ClassName).parameterizedBy(STAR)
        else if (cleanName == "table" || cleanName == "Dictionary") typeName = (typeName as ClassName).parameterizedBy(STAR, STAR)
        else if (cleanName == "Tuple" && containsGenerics) typeName = typeOf(type.substringAfter("<").substringBeforeLast(">"))
        else {
            if (containsGenerics) {
                val generics = type.substringAfter("<").substringBeforeLast(">").split(",")
                typeName = (typeName as ClassName).parameterizedBy(generics.map { typeOf(it) })
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

    private fun List<FunSpec>.removeDuplicatesBySignature(): List<FunSpec> = withIndex()
        .groupBy { (_, fs) -> fs.name to fs.parameters.map { it.type } }
        .values
        .map { sameSignature ->
            val keepList =
                if (sameSignature.size > 1)
                    sameSignature.filterNot { (_, fs) ->
                        fs.annotations.any { it.typeName == ClassName("kotlin", "Deprecated") }
                    }.ifEmpty { sameSignature }
                else
                    sameSignature

            keepList.minBy { it.index }.value
        }

    private fun List<PropertySpec>.removeDuplicatesByName(): List<PropertySpec> = withIndex()
        .groupBy { (_, p) -> p.name }
        .values
        .map { sameSignature ->
            val keepList =
                if (sameSignature.size > 1)
                    sameSignature.filterNot { (_, fs) ->
                        fs.annotations.any { it.typeName == ClassName("kotlin", "Deprecated") }
                    }.ifEmpty { sameSignature }
                else
                    sameSignature

            keepList.minBy { it.index }.value
        }
}