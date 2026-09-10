package xyz.atkdev.rbxkt

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.MapProperty

open class RbxKtGradleExtension(objectFactory: ObjectFactory) {
    val outputDir: Property<String> = objectFactory.property(String::class.java)
    val moduleKinds: MapProperty<String, String> = objectFactory.mapProperty(String::class.java, String::class.java)
        .convention(mapOf("client" to "client", "server" to "server", "shared" to "shared"))
}
