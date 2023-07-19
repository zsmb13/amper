package org.jetbrains.deft.proto.frontend

import java.nio.file.Path

context (PotatoModuleBuilder) class FragmentLinkProvider(
    override val type: FragmentDependencyType,
    private val fragmentName: String
) : FragmentLink {
    override val target: Fragment
        get() = fragments.firstOrNull { it.name == fragmentName }?.build()
            ?: error("There is no such fragment with name: $fragmentName")

    data class FragmentLinkProviderBuilder(
        val sourceFragment: FragmentBuilder,
        var name: String = "",
        var type: FragmentDependencyType = FragmentDependencyType.REFINE
    )

    companion object {
        context (PotatoModuleBuilder, FragmentBuilder) operator fun invoke(init: FragmentLinkProviderBuilder.() -> Unit): FragmentLinkProvider {
            val builder = FragmentLinkProviderBuilder(this@FragmentBuilder)
            builder.init()
            return FragmentLinkProvider(builder.type, builder.name)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FragmentLinkProvider

        return fragmentName == other.fragmentName
    }

    override fun hashCode(): Int {
        return fragmentName.hashCode()
    }
}

fun potatoModule(name: String, init: PotatoModuleBuilder.() -> Unit): PotatoModule {
    val builder = PotatoModuleBuilder(name)
    builder.init()
    return builder.build()
}

class FragmentBuilder(var name: String) {
    private val fragmentDependencies: MutableList<FragmentLink> = mutableListOf()
    private val fragmentDependants: MutableSet<FragmentLink> = mutableSetOf()
    private val externalDependencies: MutableList<Notation> = mutableListOf()
    private val parts: ClassBasedSet<FragmentPart<*>> = classBasedSet()
    private val platforms: MutableSet<Platform> = mutableSetOf()
    var src: Path? = null

    context (PotatoModuleBuilder) fun dependsOn(
        to: String, init: FragmentLinkProvider.FragmentLinkProviderBuilder.() -> Unit = {}
    ) {
        fragmentDependencies.add(FragmentLinkProvider {
            name = to
            init()
        })
    }

    context (PotatoModuleBuilder) fun dependant(
        to: String, init: FragmentLinkProvider.FragmentLinkProviderBuilder.() -> Unit = {}
    ) {
        fragmentDependants.add(FragmentLinkProvider {
            name = to
            init()
        })
    }

    fun kotlinPart(init: KotlinFragmentPartBuilder.() -> Unit) {
        val builder = KotlinFragmentPartBuilder()
        builder.init()
        parts.add(builder.build())
    }

    fun javaPart(init: JavaPartBuilder.() -> Unit) {
        val builder = JavaPartBuilder()
        builder.init()
        parts.add(builder.build())
    }

    fun build(): LeafFragment {
        return object : LeafFragment {
            override val platform: Platform
                get() = this@FragmentBuilder.platforms.single()
            override val name: String
                get() = this@FragmentBuilder.name
            override val fragmentDependencies: List<FragmentLink>
                get() = this@FragmentBuilder.fragmentDependencies
            override val fragmentDependants: List<FragmentLink>
                get() = this@FragmentBuilder.fragmentDependants.toList()
            override val externalDependencies: List<Notation>
                get() = this@FragmentBuilder.externalDependencies
            override val parts: ClassBasedSet<FragmentPart<*>>
                get() = this@FragmentBuilder.parts.toClassBasedSet()
            override val platforms: Set<Platform>
                get() = this@FragmentBuilder.platforms
            override val isTest: Boolean
                get() = false
            override val isDefault: Boolean
                get() = true
            override val src: Path?
                get() = this@FragmentBuilder.src
        }
    }
}

class KotlinFragmentPartBuilder {
    var languageVersion: String? = null
    var apiVersion: String? = null
    var progressiveMode: Boolean? = null
    val languageFeatures: MutableList<String> = mutableListOf()
    val optIns: MutableList<String> = mutableListOf()
    fun build(): KotlinPart =
        KotlinPart(
            languageVersion = languageVersion,
            apiVersion = apiVersion,
            allWarningsAsErrors = null,
            debug = null,
            progressiveMode = progressiveMode,
            languageFeatures = languageFeatures,
            optIns = optIns,
            linkerOpts = emptyList(),
        )
}

class PotatoModuleBuilder(var name: String) {
    var type: PotatoModuleType = PotatoModuleType.APPLICATION
    var source: PotatoModuleSource = PotatoModuleProgrammaticSource
    val fragments: MutableList<FragmentBuilder> = mutableListOf()
    private val artifacts = emptyList<Artifact>()
    private val parts = classBasedSet<ModulePart<*>>()

    fun fragment(name: String, init: FragmentBuilder.() -> Unit): FragmentBuilder {
        val builder = FragmentBuilder(name)
        builder.init()
        fragments.add(builder)
        return builder
    }

    fun build(): PotatoModule {
        return object : PotatoModule {
            override val userReadableName: String
                get() = this@PotatoModuleBuilder.name
            override val type: PotatoModuleType
                get() = this@PotatoModuleBuilder.type
            override val source: PotatoModuleSource
                get() = this@PotatoModuleBuilder.source
            override val fragments: List<Fragment>
                get() = this@PotatoModuleBuilder.fragments.map { it.build() }
            override val artifacts: List<Artifact>
                get() = this@PotatoModuleBuilder.artifacts
            override val parts: ClassBasedSet<ModulePart<*>>
                get() = this@PotatoModuleBuilder.parts
        }
    }
}

class JavaPartBuilder {
    var mainClass: String? = null
    var packagePrefix: String? = null
    var target: String? = null
    var source: String? = null

    fun build(): JavaPart {
        return JavaPart(
            mainClass = mainClass,
            packagePrefix = packagePrefix,
            target = target,
            source = source,
        )
    }
}
