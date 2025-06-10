package xyz.atkdev.rbxkt

import xyz.atkdev.rbxkt.BuildConfig.ANNOTATIONS_LIBRARY_COORDINATES
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption

@Suppress("unused") // Used via reflection.
class RbxKtGradlePlugin : KotlinCompilerPluginSupportPlugin {
    override fun apply(target: Project) {
        target.extensions.create("rbxkt", RbxKtGradleExtension::class.java)
        target.afterEvaluate {
            val ext = target.extensions.getByType(RbxKtGradleExtension::class.java)
            if (!ext.outputDir.isPresent) {
                ext.outputDir.set(target.layout.buildDirectory.dir("out").get().asFile.absolutePath)
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun getCompilerPluginId(): String = BuildConfig.KOTLIN_PLUGIN_ID

    override fun getPluginArtifact(): SubpluginArtifact = SubpluginArtifact(
        groupId = BuildConfig.KOTLIN_PLUGIN_GROUP,
        artifactId = BuildConfig.KOTLIN_PLUGIN_NAME,
        version = BuildConfig.KOTLIN_PLUGIN_VERSION,
    )

    override fun applyToCompilation(
        kotlinCompilation: KotlinCompilation<*>
    ): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project

        kotlinCompilation.dependencies {
            implementation(ANNOTATIONS_LIBRARY_COORDINATES)
        }

        return project.provider {
            val extension = project.extensions.getByType(RbxKtGradleExtension::class.java)
            val outputDir = extension.outputDir.orNull ?: "${project.layout.buildDirectory.get().asFile.absolutePath}/out"

            listOf(
                SubpluginOption("outputDir", outputDir)
            )
        }
    }
}
