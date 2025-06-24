package types.generator

import annotations.LuauName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.coroutines.*
import types.fetch.GithubApi
import types.models.*
import types.utils.*
import java.io.File

private val logger = System.getLogger("type gen")

internal interface DocsModel {
    val name: String
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

internal class RobloxTypeGenerator() {
    companion object {
        internal const val CORRECTIONS_RAW_GITHUB_URL =
            "https://raw.githubusercontent.com/JohnnyMorganz/luau-lsp/refs/heads/main/scripts/Corrections.json"

        const val PACKAGE_NAME = "xyz.atkdev.rbxkt.api"
    }

    internal lateinit var annotations: Map<String, AnnotationSpec>
    internal lateinit var enums: Map<String, ClassName>
    internal lateinit var datatypes: Map<String, ClassName>
    internal lateinit var classes: Map<String, ClassName>

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun generate() {
        val corrections = getCorrections()

        val suppressAnnotation = AnnotationSpec.builder(Suppress::class)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .addMember(
                "%L",
                "\"unused\", \"unused_parameter\", \"RedundantVisibilityModifier\", \"RemoveRedundantQualifierName\", \"SpellCheckingInspection\", \"DEPRECATION\""
            )
            .build()

        val annotationFileSpec = FileSpec.builder(PACKAGE_NAME, "RobloxAnnotations").addAnnotation(suppressAnnotation)
        val enumModelSpec = FileSpec.builder("$PACKAGE_NAME.enums", "RobloxEnums").addAnnotation(suppressAnnotation)
        val dataTypeModelSpec =
            FileSpec.builder("$PACKAGE_NAME.datatypes", "RobloxDatatypes").addAnnotation(suppressAnnotation)
        val classModelSpec =
            FileSpec.builder("$PACKAGE_NAME.classes", "RobloxClasses").addAnnotation(suppressAnnotation)

        val (enumModels, dataTypeModels, classModels) = coroutineScope {
            val enums = async { GithubApi.getYamlFiles<EnumModel>("enums") }
            val datatypes = async { GithubApi.getYamlFiles<DatatypeModel>("datatypes") }
            val classes = async { GithubApi.getYamlFiles<ClassModel>("classes") }

            awaitAll(enums, datatypes, classes)

            Triple(enums.getCompleted(), datatypes.getCompleted(), classes.getCompleted())
        }


        enums = enumModels.keys.associateWith { ClassName("$PACKAGE_NAME.enums", it) }
        datatypes = dataTypeModels.keys.associateWith { ClassName("$PACKAGE_NAME.datatypes", it) }
        classes = classModels.keys.associateWith { ClassName("$PACKAGE_NAME.classes", it) }

        annotations = generateAnnotations(annotationFileSpec)

        coroutineScope {
            launch { generateEnums(enumModels, enumModelSpec) }
            launch { generateDataTypes(dataTypeModels, dataTypeModelSpec) }
            launch { generateClasses(classModels, classModelSpec) }
        }

        annotationFileSpec.build().writeTo(File(System.getProperty("user.dir") + "/src/api/"))
        enumModelSpec.build().writeTo(File(System.getProperty("user.dir") + "/src/api/"))
        dataTypeModelSpec.build().writeTo(File(System.getProperty("user.dir") + "/src/api/"))
        classModelSpec.build().writeTo(File(System.getProperty("user.dir") + "/src/api/"))
    }

    private suspend fun getCorrections(): Map<String, Map<String, CorrectionData>> {
        val corrections = GithubApi.getJsonFile<CorrectionsModel>(CORRECTIONS_RAW_GITHUB_URL, false)
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
        val schema = GithubApi.getJsonFile<SchemaModel>("tools/schemas/engine/classes.json")
        val combinedNames =
            (schema.definitions.tags.items.enum + schema.definitions.threadSafety.enum + schema.definitions.securityTags.enum)
                .filterNot { it == "Deprecated" }
                .toMutableSet()

        combinedNames.forEach {
            fileSpec.addType(
                TypeSpec.annotationBuilder(it)
                    .addModifiers(KModifier.INTERNAL)
                    .build()
            )
        }

        return combinedNames.associateWith { AnnotationSpec.builder(ClassName(PACKAGE_NAME, it)).build() }
    }

    private fun generateEnums(files: Map<String, EnumModel>, fileSpec: FileSpec.Builder) {
        files.forEach { (name, file) ->
            val builder = TypeSpec.enumBuilder(name)
                .addSummary(file.summary)
                .addDeprecation(file.deprecationMessage)
                .addTags(file.tags)

            file.items.forEach { item ->
                val anonClassBuilder = TypeSpec.anonymousClassBuilder()
                    .addSummary(item.summary)
                    .addDeprecation(item.deprecationMessage)
                    .addTags(item.tags)

                builder.addEnumConstant(item.name, anonClassBuilder.build())
            }

            val typeSpec = builder.build()
            fileSpec.addType(typeSpec)
        }
    }

    private suspend fun generateDataTypes(files: Map<String, DatatypeModel>, fileSpec: FileSpec.Builder) {
        files.forEach { (name, file) ->
            val builder = TypeSpec.classBuilder(name)
                .addSummary(file.summary)
                .addDeprecation(file.deprecationMessage)
                .addTags(file.tags)

            var hasPrimaryConstructor = false
            val companionBuilder = TypeSpec.companionObjectBuilder()
            file.constructors?.forEach { constructor ->
                val funcName = constructor.name.substringAfter(".")
                val isDefaultBuilder = funcName == "new"

                val funcBuilder =
                    if (isDefaultBuilder) FunSpec.constructorBuilder() else FunSpec.builder(funcName.toCamelCase())
                        .addSummary(constructor.summary)
                        .addDeprecation(constructor.deprecationMessage)
                        .addTags(constructor.tags)

                if (funcName != funcName.toCamelCase()) funcBuilder.addLuauName(funcName)
                if (!isDefaultBuilder) funcBuilder.addModifiers(KModifier.EXTERNAL)
                constructor.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }
                if (!isDefaultBuilder) funcBuilder.returns(datatypes[name]!!)

                hasPrimaryConstructor = isDefaultBuilder

                if (isDefaultBuilder) builder.primaryConstructor(funcBuilder.build()) else companionBuilder.addFunction(
                    funcBuilder.build()
                )
            }

            file.constants?.forEach { constant ->
                val propBuilder = PropertySpec.builder(constant.name.substringAfter("."), typeOf(constant.type))
                    .addSummary(constant.summary)
                    .addDeprecation(constant.deprecationMessage)
                    .addTags(constant.tags)
                    .initializer("TODO()")

                companionBuilder.addProperty(propBuilder.build())
            }

            val constantNames = file.constants?.map { it.name.substringAfter(".") } ?: emptyList()
            file.properties?.let { property ->
                builder
                    .addProperties(
                        property
                        .filterNot { it.name in constantNames }
                        .map(::generateProperty))
            }

            file.methods?.let { methods ->
                builder.addFunctions(methods.flatMap {
                    generateMethod(
                        datatypes[name]!!,
                        it,
                        companionBuilder
                    )
                })
            }

            file.mathOperations?.forEach { mathOperation ->
                val operation = mathOperation.operation.fromOperator()
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
                        .addParameter(
                            "builder", LambdaTypeName.get(
                                datatypes[name]!!,
                                emptyList(),
                                UNIT
                            )
                        )
                        .callThisConstructor()
                        .build()
                )
                if (!hasPrimaryConstructor) builder.primaryConstructor(
                    FunSpec.constructorBuilder().addModifiers(KModifier.PRIVATE).build()
                )
            }

            if (companionBuilder.propertySpecs.isNotEmpty() || companionBuilder.funSpecs.isNotEmpty() || companionBuilder.typeSpecs.isNotEmpty()) builder.addType(
                companionBuilder.build()
            )
            val dataType = builder.build()
            fileSpec.addType(dataType)
        }
    }

    private suspend fun generateLuauGlobals() {}
    private suspend fun generateRobloxGlobals() {}
    private suspend fun generateLibraries() {}

    private suspend fun generateClasses(files: Map<String, ClassModel>, fileSpec: FileSpec.Builder) {
        val interfaces = files.mapValues { (name, file) ->
            if (name == "Studio") return@mapValues TypeSpec.objectBuilder("Studio").build()
            val interfaceBuilder = TypeSpec.interfaceBuilder("I${name}")

            val companionBuilder = TypeSpec.companionObjectBuilder()
                .addDeprecation(file.deprecationMessage)
                .addTags(file.tags)

            file.properties?.let { prop -> interfaceBuilder.addProperties(prop.map { generateProperty(it, false) }) }

            file.methods?.let { methods ->
                interfaceBuilder.addFunctions(methods.flatMap {
                    generateMethod(
                        classes[name]!!,
                        it,
                        companionBuilder,
                        false
                    )
                })
            }

            file.events?.forEach { event ->
                val eventName = event.name.substringAfter(".")
                val propertyBuilder = PropertySpec.builder(eventName.toCamelCase(), datatypes["RBXScriptConnection"]!!)
                    .addSummary(event.summary)
                    .addDeprecation(event.deprecationMessage)
                    .addTags(event.tags)

                if (eventName != eventName.toCamelCase()) propertyBuilder.addLuauName(eventName)
                if (eventName == "Changed" && name != "Object") propertyBuilder.addModifiers(KModifier.OVERRIDE)
                interfaceBuilder.addProperty(propertyBuilder.build())
            }

            file.inherits?.forEach {
                interfaceBuilder.addSuperinterface(ClassName("$PACKAGE_NAME.classes", "I${it}"))
            }

            if (companionBuilder.propertySpecs.isNotEmpty() || companionBuilder.funSpecs.isNotEmpty() || companionBuilder.typeSpecs.isNotEmpty()) interfaceBuilder.addType(
                companionBuilder.build()
            )

            if (name == "Instance") {
                interfaceBuilder.addFunction(
                    FunSpec.builder("get")
                        .addParameter("name", String::class)
                        .addModifiers(KModifier.OPERATOR, KModifier.ABSTRACT)
                        .returns(classes["Instance"]!!.copy(nullable = true))
                        .build()
                )
            }

            interfaceBuilder.dedupeFunSpecs()
            interfaceBuilder.dedupePropertySpecs()

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
            val builder =
                if (file.tags.contains("Service")) TypeSpec.objectBuilder(name) else TypeSpec.classBuilder(name)
                    .addSummary(file.summary)

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
                        .addParameter(
                            "builder", LambdaTypeName.get(
                                classes[name]!!,
                                emptyList(),
                                UNIT
                            )
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

            builder.dedupeFunSpecs()
            builder.dedupePropertySpecs()

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
                .addSummary(ret.summary)

            constructor.addParameter(param.build())

            val prop = PropertySpec.builder(name, type)
                .initializer(name)
                .addSummary(ret.summary)

            classBuilder.addProperty(prop.build())
        }

        return classBuilder.primaryConstructor(constructor.build()).build()
    }

    private fun generateEvent(event: ClassModel.ClassEvent, initialize: Boolean = true): PropertySpec {
        val propertyBuilder =
            PropertySpec.builder(event.name.substringAfter("."), ClassName(PACKAGE_NAME, "RBXScriptConnection"))
                .addSummary(event.summary)
                .addDeprecation(event.deprecationMessage)
                .addTags(event.tags)
        if (initialize) propertyBuilder.initializer("TODO()")

        return propertyBuilder.build()
    }

    private fun generateProperty(property: Property, initialize: Boolean = true): PropertySpec {
        val propName = property.name.substringAfter(".")
        val type = typeOf(property.type)
        val propBuilder = PropertySpec.builder(propName.toCamelCase(), type).mutable(true)
            .addSummary(property.summary)
            .addDeprecation(property.deprecationMessage)
            .addTags(property.tags + listOfNotNull(property.threadSafety))

        if (propName != propName.toCamelCase()) propBuilder.addLuauName(propName)

        if (initialize) {
            if (type == Boolean::class.asClassName())
                propBuilder.initializer("false")
            else if (type.isNullable)
                propBuilder.initializer("null")
            else
                propBuilder.addModifiers(KModifier.LATEINIT)
        }

        return propBuilder.build()
    }

    private fun generateMethod(
        className: ClassName,
        method: Method,
        companion: TypeSpec.Builder,
        external: Boolean = true
    ): List<FunSpec> {
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

            for (variantParams in paramVariants.cartesianProduct()) {
                val overload = method.copy(parameters = variantParams)
                methods += generateMethod(className, overload, companion)
            }

            return methods
        }

        val methodName = method.name.substringAfter(":")
        val funcBuilder = FunSpec.builder(methodName.toCamelCase())
            .addSummary(method.summary)
            .addDeprecation(method.deprecationMessage)
            .addTags(method.tags)

        if (methodName != methodName.toCamelCase()) funcBuilder.addLuauName(methodName)
        if (external) funcBuilder.addModifiers(KModifier.EXTERNAL) else funcBuilder.addModifiers(KModifier.ABSTRACT)

        method.parameters?.let { funcBuilder.addParameters(it.map(::generateParameter)) }

        if (method.returns.size > 1) {
            val returnClass = generateReturnClass(methodName, method.returns)
            companion.addType(returnClass)
            funcBuilder.returns(
                className
                    .nestedClass("Companion")
                    .nestedClass(returnClass.name!!)
            )
        } else if (method.returns.size == 1 && method.returns[0].type != "()") {
            funcBuilder.returns(typeOf(method.returns[0].type))
        }

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

        if (cleanName == "Array" && !containsGenerics || cleanName == "function" || cleanName == "Function") typeName =
            (typeName as ClassName).parameterizedBy(STAR)
        else if (cleanName == "table" || cleanName == "Dictionary") typeName =
            (typeName as ClassName).parameterizedBy(STAR, STAR)
        else if (cleanName == "Tuple" && containsGenerics) typeName =
            typeOf(type.substringAfter("<").substringBeforeLast(">"))
        else {
            if (containsGenerics) {
                val generics = type.substringAfter("<").substringBeforeLast(">").split(",")
                typeName = (typeName as ClassName).parameterizedBy(generics.map { typeOf(it) })
            }
        }

        return typeName.copy(isNullable)
    }
}