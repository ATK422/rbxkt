package xyz.atkdev.rbxkt.luau

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.kotlin.ir.backend.js.utils.nameWithoutExtension
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithVisibility
import org.jetbrains.kotlin.ir.declarations.IrFile
import org.jetbrains.kotlin.ir.declarations.path
import org.jetbrains.kotlin.ir.overrides.isNonPrivate
import java.io.File

@Serializable
data class ExportEntry(val exportName: String, val filePath: String)

@Serializable
data class PackageExports(val packageName: String, val exports: List<ExportEntry>)

@Serializable
data class ExportMap(val packages: List<PackageExports>)

object ExportStore {
    fun save(file: File, pkgExports: Map<String, Map<String, String>>) {
        val exportMap = ExportMap(
            packages = pkgExports.map { (pkg, exports) ->
                PackageExports(
                    packageName = pkg,
                    exports = exports.map { (name, path) ->
                        ExportEntry(exportName = name, filePath = path)
                    }
                )
            }
        )
        file.parentFile.mkdirs()
        file.writeText(Json.encodeToString(exportMap))
    }

    fun load(file: File): MutableMap<String, MutableMap<String, String>> {
        if (!file.exists()) return mutableMapOf()

        val exportMap = Json.decodeFromString<ExportMap>(file.readText())
        val result = mutableMapOf<String, MutableMap<String, String>>()

        for (pkg in exportMap.packages) {
            val map = mutableMapOf<String, String>()
            for (entry in pkg.exports) {
                map[entry.exportName] = entry.filePath
            }
            result[pkg.packageName] = map
        }

        return result
    }
}

object LuauExportAnalyzer {
    private var file: File? = null
    var pkgExports: MutableMap<String, MutableMap<String, String>> = mutableMapOf()

    fun analyze(path: File, irFiles: List<IrFile>) {
        file = File(path, "exports.json")

        pkgExports = ExportStore.load(file!!)
        for (irFile in irFiles) {
            val pkgName = irFile.packageFqName.asString()
            val fileExports = analyzeFile(irFile)
            pkgExports[pkgName] = fileExports
        }

        ExportStore.save(file!!, pkgExports)
    }

    fun getFilePathByExportAndPkg(exportName: String, pkgName: String): String? {
        val fileExports = pkgExports[pkgName]?: return null
        val filePath = fileExports[exportName]
        return filePath
    }

    fun getExportsForFile(irFile: IrFile): List<LuauIdentifier> {
        val pkgName = irFile.packageFqName.asString()
        val fileExports = pkgExports[pkgName]?: return listOf()
        return fileExports.keys.map { LuauIdentifier(it) }
    }

    private fun analyzeFile(irFile: IrFile): MutableMap<String, String> {
        val declarations = irFile.declarations
        val filePath = irFile.path
        var fileExports: MutableMap<String, String> = mutableMapOf()

        for (declaration in declarations) {
            if (declaration is IrDeclarationWithVisibility && declaration.isNonPrivate) {
                val name = (declaration as IrDeclarationWithName).name.asString()
                fileExports[name] = filePath
            }
        }

        return fileExports
    }
}