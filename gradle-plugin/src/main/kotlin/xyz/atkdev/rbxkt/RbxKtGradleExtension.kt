package xyz.atkdev.rbxkt

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

open class RbxKtGradleExtension(objectFactory: ObjectFactory) {
    val outputDir: Property<String> = objectFactory.property(String::class.java)
}
