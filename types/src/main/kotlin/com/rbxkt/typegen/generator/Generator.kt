package com.rbxkt.typegen.generator

import com.rbxkt.typegen.fetch.GithubApi
import com.rbxkt.typegen.models.ClassModel
import com.rbxkt.typegen.models.CorrectionsModel
import com.rbxkt.typegen.models.DatatypeModel
import com.rbxkt.typegen.models.EnumModel
import com.rbxkt.typegen.models.Method
import com.rbxkt.typegen.models.Parameter
import com.rbxkt.typegen.models.Property
import com.rbxkt.typegen.models.Return
import com.rbxkt.typegen.models.SchemaModel
import com.rbxkt.typegen.utils.addDeprecation
import com.rbxkt.typegen.utils.addJvmName
import com.rbxkt.typegen.utils.addLuauName
import com.rbxkt.typegen.utils.addSummary
import com.rbxkt.typegen.utils.addTags
import com.rbxkt.typegen.utils.cartesianProduct
import com.rbxkt.typegen.utils.dedupeFunSpecs
import com.rbxkt.typegen.utils.dedupePropertySpecs
import com.rbxkt.typegen.utils.fromOperator
import com.rbxkt.typegen.utils.toCamelCase
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.coroutines.*
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

internal class RobloxTypeGenerator(private val githubApi: GithubApi) {
    companion object {
        internal const val CORRECTIONS_RAW_GITHUB_URL =
            "https://raw.githubusercontent.com/JohnnyMorganz/luau-lsp/refs/heads/main/scripts/Corrections.json"

        const val PACKAGE_NAME = "com.rbxkt.types"
    }

    internal lateinit var annotations: Map<String, AnnotationSpec>
    internal lateinit var enums: Map<String, ClassName>
    internal lateinit var datatypes: Map<String, ClassName>
    internal lateinit var classes: Map<String, ClassName>

    @OptIn(ExperimentalCoroutinesApi::class)
    internal suspend fun generate(generatedDir: File = File(System.getProperty("user.dir"), "src/generated")) {
        val corrections = getCorrections()

        val suppressAnnotation = AnnotationSpec.builder(Suppress::class)
            .useSiteTarget(AnnotationSpec.UseSiteTarget.FILE)
            .addMember(
                "%L",
                "\"unused\", \"unused_parameter\", \"RedundantVisibilityModifier\", \"RemoveRedundantQualifierName\", \"SpellCheckingInspection\", \"DEPRECATION\", \"INAPPLICABLE_JVM_NAME\""
            )
            .build()

        val annotationFileSpec = FileSpec.builder(PACKAGE_NAME, "RobloxAnnotations").addAnnotation(suppressAnnotation)
        val enumModelSpec = FileSpec.builder("$PACKAGE_NAME.enums", "RobloxEnums").addAnnotation(suppressAnnotation)
        val dataTypeModelSpec =
            FileSpec.builder("$PACKAGE_NAME.datatypes", "RobloxDatatypes").addAnnotation(suppressAnnotation)
        val classModelSpec =
            FileSpec.builder("$PACKAGE_NAME.classes", "RobloxClasses").addAnnotation(suppressAnnotation)

        val (enumModels, dataTypeModels, classModels) = coroutineScope {
            val enums = async { githubApi.getYamlFiles<EnumModel>("enums") }
            val datatypes = async { githubApi.getYamlFiles<DatatypeModel>("datatypes") }
            val classes = async { githubApi.getYamlFiles<ClassModel>("classes") }

            awaitAll(enums, datatypes, classes)

            Triple(enums.getCompleted(), datatypes.getCompleted(), classes.getCompleted())
        }

        logger.log(System.Logger.Level.INFO, "Loaded ${enumModels.size} enums, ${dataTypeModels.size} datatypes, and ${classModels.size} classes")

        enums = enumModels.keys.associateWith { ClassName("$PACKAGE_NAME.enums", it) }
        datatypes = dataTypeModels.keys.associateWith { ClassName("$PACKAGE_NAME.datatypes", it) }
        classes = classModels.keys.associateWith { ClassName("$PACKAGE_NAME.classes", it) }

        annotations = generateAnnotations(annotationFileSpec)

        coroutineScope {
            launch { generateEnums(enumModels, enumModelSpec) }
            launch { generateDataTypes(dataTypeModels, dataTypeModelSpec) }
            launch { generateClasses(classModels, classModelSpec) }
        }
        
        generatedDir.deleteRecursively()
        generatedDir.mkdirs()

        annotationFileSpec.build().writeTo(generatedDir)
        enumModelSpec.build().writeTo(generatedDir)
        dataTypeModelSpec.build().writeTo(generatedDir)
        classModelSpec.build().writeTo(generatedDir)
    }

    private suspend fun getCorrections(): Map<String, Map<String, CorrectionData>> {
        val corrections = githubApi.getJsonFile<CorrectionsModel>(CORRECTIONS_RAW_GITHUB_URL, false)
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
                    if (member.returnType != null && (member.returnType.name != null || member.returnType.generic != null))
                        listOf(member.returnType.name ?: member.returnType.generic!!)
                    else
                        member.tupleReturn?.map { it.name!! }
                )
            }
        }
    }

    private suspend fun generateAnnotations(fileSpec: FileSpec.Builder): Map<String, AnnotationSpec> {
        val schema = githubApi.getJsonFile<SchemaModel>("tools/schemas/engine/classes.json")
        val combinedNames = schema.annotationNames()

        val nativeAnnotation = TypeSpec.annotationBuilder("Native")
            .addAnnotation(
                AnnotationSpec.builder(Target::class)
                    .addMember("%T.FILE", AnnotationTarget::class)
                    .build()
            )
            .build()

        val optimizeAnnotation = TypeSpec.annotationBuilder("Optimize")
            .addAnnotation(
                AnnotationSpec.builder(Target::class)
                    .addMember("%T.FILE", AnnotationTarget::class)
                    .build()
            )
            .addProperty(
                PropertySpec.builder("level", Int::class)
                    .initializer("level")
                    .build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("level", Int::class)
                    .build()
            )
            .build()

        val entrypointAnnotation = TypeSpec.annotationBuilder("Entrypoint")
            .addKdoc("Marks the startup function for a client or server compilation configured in rbxkt.moduleKinds.\n")
            .addAnnotation(
                AnnotationSpec.builder(Target::class)
                    .addMember("%T.FUNCTION", AnnotationTarget::class)
                    .build()
            )
            .build()

        val luauNameAnnotation = TypeSpec.annotationBuilder("LuauName")
            .addProperty(
                PropertySpec.builder("name", String::class)
                    .initializer("name")
                    .build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder()
                    .addParameter("name", String::class)
                    .build()
            )
            .build()

        fileSpec.addType(nativeAnnotation)
        fileSpec.addType(optimizeAnnotation)
        fileSpec.addType(entrypointAnnotation)
        fileSpec.addType(luauNameAnnotation)

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
                    if (isDefaultBuilder) FunSpec.constructorBuilder() else FunSpec.Companion.builder(funcName.toCamelCase())
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

            if (name == "RBXScriptSignal") {
                builder.addFunction(FunSpec.builder("invoke")
                    .addModifiers(KModifier.EXTERNAL, KModifier.OPERATOR)
                    .addLuauName("Connect")
                    .addParameter("callback", Function::class.asClassName().parameterizedBy(STAR))
                    .returns(datatypes.getValue("RBXScriptConnection"))
                    .build())
                // Function<*> alone supplies no signature for trailing-lambda inference.
                for ((kotlinName, luauName) in mapOf(
                    "invoke" to "Connect", "connect" to "Connect",
                    "connectParallel" to "ConnectParallel", "once" to "Once"
                )) {
                    builder.addFunction(FunSpec.builder(kotlinName)
                        .addModifiers(KModifier.EXTERNAL)
                        .apply { if (kotlinName == "invoke") addModifiers(KModifier.OPERATOR) }
                        .addLuauName(luauName)
                        .addParameter("callback", LambdaTypeName.get(returnType = UNIT))
                        .returns(datatypes.getValue("RBXScriptConnection"))
                        .build())
                }
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

            file.properties?.let { prop ->
                interfaceBuilder.addProperties(prop
                    .filter { it.name != "Playing" && name != "Sound" }
                    .map {
                    generateProperty(it, false).toBuilder()
                        .getter(FunSpec.builder("get()")
                            .addStatement("return TODO()")
                            .build()
                        )
                        .setter(FunSpec.builder("set()")
                            .addParameter(
                                "v", Nothing::class.asTypeName()
                            )
                            .addStatement("return TODO()")
                            .build()
                        )
                        .build()
                })
            }

            file.methods?.let { methods ->
                interfaceBuilder.addFunctions(methods.flatMap {
                    generateMethod(
                        classes[name]!!,
                        it,
                        companionBuilder,
                        false
                    ).map { method ->
                        method.toBuilder().addStatement("return TODO()").build()
                    }
                })
            }

            file.events?.forEach { event ->
                val eventName = event.name.substringAfter(".")
                val propertyBuilder = PropertySpec.builder(eventName.toCamelCase(), datatypes["RBXScriptSignal"]!!)
                    .addSummary(event.summary)
                    .addDeprecation(event.deprecationMessage)
                    .getter(FunSpec.getterBuilder()
                        .clearBody()
                        .addStatement("return TODO()")
                        .build()
                    )
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
                val t = TypeVariableName("T", classes["Instance"]!!)
                interfaceBuilder.addFunction(
                    FunSpec.builder("get")
                        .addTypeVariable(t)
                        .addParameter("name", String::class)
                        .addModifiers(KModifier.OPERATOR) // KModifier.ABSTRACT
                        .addCode("return TODO()")
                        .returns(t.copy(nullable = true))
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
            if (name != "Instance" && !file.inherits?.contains("Object")!!) {
                builder.superclass(classes["Instance"]!!)
            }
            else builder.addModifiers(KModifier.OPEN)

            if (!file.tags.contains("Service")) {
                if (!file.tags.contains("NotCreatable") || name == "Instance") {
                    builder.primaryConstructor(FunSpec.constructorBuilder().build())
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
            val newProperties = newInterface.propertySpecs.map { prop ->
                val builder = prop.toBuilder()
                // builder.annotations.removeIf { it.typeName != LuauName::class.asTypeName() }
                return@map builder.build()
            }
            newInterface.propertySpecs.clear()
            newInterface.addProperties(newProperties)

            val newFunctions = newInterface.funSpecs.map { func ->
                val builder = func.toBuilder()
                // builder.annotations.removeIf { it.typeName != LuauName::class.asTypeName() }
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
            PropertySpec.builder(event.name.substringAfter("."), datatypes.getValue("RBXScriptSignal"))
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

        if (methodName.toCamelCase() == "toString" && method.parameters.orEmpty().isEmpty()) {
            funcBuilder.addModifiers(KModifier.OVERRIDE)
        }

        if (methodName != methodName.toCamelCase()) {
            funcBuilder.addLuauName(methodName)
        }

        if (external) funcBuilder.addModifiers(KModifier.EXTERNAL)

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

        if (methodName != methodName.toCamelCase()) {
            if (methodName.startsWith("Set") || methodName.startsWith("Get")) {
                funcBuilder.addJvmName(methodName)
            }
        }

        return listOf(funcBuilder.build())
    }

    private fun generateParameter(parameter: Parameter): ParameterSpec {
        var cleanName = parameter.name.replace("...", "")
        if (cleanName.isEmpty()) cleanName = "a0"
        val type = typeOf(parameter.type, parameter.default == "nil" || parameter.default == "{}")
        val builder = ParameterSpec.builder(cleanName.toCamelCase(), type)
        val default = parameter.default
        if (default?.startsWith("Enum.") == true && default.removePrefix("Enum.").substringBefore('.') !in enums) {
            // Upstream can retain defaults for enums it no longer documents.
            // Keep the optional argument and original value in this compile-time stub.
            return builder.defaultValue("TODO(%S)", "Roblox default: $default").build()
        }
        if (type == datatypes["User"] && default?.startsWith("U1.") == true) {
            return builder.defaultValue("%T.fromString(%S)", type, default).build()
        }
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
            "List" -> List::class.asClassName()
            "bool", "boolean" -> Boolean::class.asClassName()
            "table" -> Map::class.asClassName()
            "Dictionary" -> Map::class.asClassName()
            "function", "Function" -> Function::class.asClassName()
            else -> enums[cleanName] ?: classes[cleanName] ?: datatypes[cleanName] ?: Any::class.asClassName()
        }

        if (cleanName in setOf("Array", "List") && !containsGenerics || cleanName == "function" || cleanName == "Function") typeName =
            (typeName as ClassName).parameterizedBy(STAR)
        else if (cleanName == "table" || cleanName == "Dictionary") typeName =
            (typeName as ClassName).parameterizedBy(STAR, STAR)
        else if (cleanName == "Tuple" && containsGenerics) typeName =
            typeOf(type.substringAfter("<").substringBeforeLast(">"))
        else {
            if (containsGenerics && cleanName in setOf("Array", "List")) {
                val generics = type.substringAfter("<").substringBeforeLast(">").split(",")
                typeName = (typeName as ClassName).parameterizedBy(generics.map { typeOf(it) })
            }
        }

        return typeName.copy(isNullable)
    }
}
