package org.jetbrains.deft.proto.frontend

import org.jetbrains.deft.proto.frontend.model.PlainArtifact
import org.jetbrains.deft.proto.frontend.model.PlainFragment
import org.jetbrains.deft.proto.frontend.model.PlainLeafFragment
import org.jetbrains.deft.proto.frontend.model.TestPlainArtifact
import java.nio.file.Path
import java.util.*

internal data class MutableFragmentDependency(val target: FragmentBuilder, val dependencyKind: DependencyKind) {
    enum class DependencyKind {
        Friend, Refines
    }
}

internal data class FragmentBuilder(
    val name: String,
    val platforms: Set<Platform>,
    val dependencies: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val dependants: MutableSet<MutableFragmentDependency> = mutableSetOf(),
    val externalDependencies: MutableSet<Notation> = mutableSetOf(),

    var isTest: Boolean = false,

    var isDefault: Boolean = true,

    var isLeaf: Boolean = false,

    /**
     * These are all variants, that this fragment should be included in.
     * Thus, "common" fragment will contain all variants.
     */
    val variants: MutableSet<String> = mutableSetOf(),
    var alias: String? = null,

    // parts
    var kotlin: KotlinPartBuilder? = KotlinPartBuilder {},
    var junit: JunitPartBuilder? = JunitPartBuilder {},

    // Leaf parts.
    var android: AndroidPartBuilder? = AndroidPartBuilder {},
    var native: NativePartBuilder? = NativePartBuilder {},
    var java: JavaPartBuilder? = JavaPartBuilder {},
    var publishing: PublishingPartBuilder? = PublishingPartBuilder {},
    var compose: ComposePartBuilder? = ComposePartBuilder {}
) {

    lateinit var src: Path
    lateinit var resourcesPath: Path

    /**
     * Simple copy ctor.
     */
    constructor(
        name: String,
        copyFrom: FragmentBuilder
    ) : this(name, copyFrom.platforms) {
        variants.addAll(copyFrom.variants)
        isTest = copyFrom.isTest
        dependencies.addAll(copyFrom.dependencies)
        alias = copyFrom.alias
        isDefault = copyFrom.isDefault
    }

    fun addDependency(mutableFragmentDependency: MutableFragmentDependency) {
        dependencies.add(mutableFragmentDependency)
        mutableFragmentDependency.target.dependants.add(
            MutableFragmentDependency(
                this, mutableFragmentDependency.dependencyKind
            )
        )
    }

    fun removeDependency(mutableFragmentDependency: MutableFragmentDependency) {
        dependencies.remove(mutableFragmentDependency)
        mutableFragmentDependency.target.dependants.removeIf { it.target == this }
    }

    fun removeDependencies(mutableFragmentDependencies: Set<MutableFragmentDependency>) {
        mutableFragmentDependencies.forEach { removeDependency(it) }
    }

    fun addDependencies(mutableFragmentDependencies: Set<MutableFragmentDependency>) {
        mutableFragmentDependencies.forEach { addDependency(it) }
    }

    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun build(): Fragment = state.computeIfAbsent(this) {
        if (it.isLeaf) PlainLeafFragment(it) else PlainFragment(it)
    }

    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun buildLeaf(): LeafFragment = build() as PlainLeafFragment

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FragmentBuilder

        if (name != other.name) return false
        if (platforms != other.platforms) return false
        if (externalDependencies != other.externalDependencies) return false
        if (variants != other.variants) return false
        if (alias != other.alias) return false
        if (kotlin != other.kotlin) return false
        return junit == other.junit
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + platforms.hashCode()
        result = 31 * result + externalDependencies.hashCode()
        result = 31 * result + variants.hashCode()
        result = 31 * result + (alias?.hashCode() ?: 0)
        result = 31 * result + (kotlin?.hashCode() ?: 0)
        result = 31 * result + (junit?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "FragmentBuilder(name='$name', platforms=$platforms, variants=$variants, alias=$alias, src=$src)"
    }
}

internal data class ArtifactBuilder(
    val name: String,
    val platforms: Set<Platform>,
    val variants: MutableSet<String> = mutableSetOf(),
    val fragments: MutableList<FragmentBuilder> = mutableListOf(),
) {
    context (Stateful<FragmentBuilder, Fragment>, TypesafeVariants)
    fun build(): Artifact {
        if (variants.contains("test")) {
            return TestPlainArtifact(this)
        }
        return PlainArtifact(this)
    }
}

internal fun List<FragmentBuilder>.multiplyFragments(variants: List<Settings>): List<FragmentBuilder> {
    val fragments = this.toMutableList()
    // multiply
    for (variant in variants) {
        val options = variant[optionsKey]
        assert(options.isNotEmpty()) { "Options are required" }
        assert(options.count { it[isDefaultKey] } == 1) { "Default value is required and must be single" }

        // copy fragments
        val fragmentMap = buildMap {
            for (option in options) {
                val optionName = option[nameKey] ?: error("Name is required for option")
                val newFragmentList = buildList {
                    for (element in fragments) {
                        val newFragmentName = if (option[isDefaultKey])
                            element.name
                        else
                            element.name.camelMerge(optionName)

                        val newFragment = FragmentBuilder(newFragmentName, element).apply {
                            // Adjust testing sign
                            if (optionName == "test") isTest = true

                            // Place a default flag for fragments that should be built by default and
                            // for their direct test descendants.
                            isDefault = element.isDefault && option[isDefaultFragmentKey]
                                    || isTest && element.isDefault

                        }

                        // Add new variant to fragment.
                        newFragment.variants.add(optionName)
                        add(newFragment)
                    }
                }
                put(optionName, newFragmentList)
            }
        }

        // set dependencies between potatoes
        for (option in options) {
            val dependencies = option[dependsOnKey]
            val name = option[nameKey] ?: error("Name is required for option")

            val sourceFragments = fragmentMap[name] ?: error("Something went wrong")
            // correct previous old dependencies references after copying
            sourceFragments.forEach { fragment ->
                val dependenciesToRemove = mutableSetOf<MutableFragmentDependency>()
                val dependenciesToAdd = mutableSetOf<MutableFragmentDependency>()
                fragment.dependencies.forEach { sourceDependency ->
                    val targetFragment = sourceFragments.filter { it !== fragment }
                        .sortedByDescending { (sourceDependency.target.variants intersect it.variants).size }
                        .firstOrNull { it.platforms == sourceDependency.target.platforms }
                        ?: error("Something went wrong")

                    val targetDependency =
                        MutableFragmentDependency(targetFragment, sourceDependency.dependencyKind)
                    dependenciesToRemove.add(sourceDependency)
                    dependenciesToAdd.add(targetDependency)
                }
                fragment.removeDependencies(dependenciesToRemove)
                fragment.addDependencies(dependenciesToAdd)
            }

            for (dependency in dependencies) {
                val dependencyTarget = dependency.getStringValue("target")
                // add new dependencies related to copying
                fragmentMap[dependencyTarget]?.let { targetFragments ->
                    for (i in sourceFragments.indices) {
                        val kind = when (dependency.getStringValue("kind")) {
                            "friend" -> MutableFragmentDependency.DependencyKind.Friend
                            else -> MutableFragmentDependency.DependencyKind.Refines
                        }
                        sourceFragments[i].addDependency(MutableFragmentDependency(targetFragments[i], kind))
                    }
                }
            }
        }

        fragments.clear()
        for (fragmentSkeletonNodes in fragmentMap.values) {
            fragments.addAll(fragmentSkeletonNodes)
        }
    }
    return fragments
}

context (Map<String, Set<Platform>>)
internal val Set<Set<Platform>>.basicFragments: List<FragmentBuilder>
    get() {
        val platforms = this
        return buildList {
            val sortedPlatformSubsets = platforms.sortedBy { it.size }
            val reducedPlatformSet = platforms.reduce { acc, set -> acc + set }
            sortedPlatformSubsets.forEach { platformSet ->
                val (name, alias) = platformSet.toCamelCaseString()
                val fragment = FragmentBuilder(name, platformSet, alias = alias)
                addFragment(fragment, platformSet)
            }

            if (reducedPlatformSet.size > 1) {
                val fragment = FragmentBuilder("common", reducedPlatformSet)
                addFragment(fragment, reducedPlatformSet)
            }
        }
    }

context(BuildFileAware)
internal fun List<FragmentBuilder>.artifacts(
    variants: List<Settings>,
    productType: ProductType,
    platforms: Set<Platform>
): List<ArtifactBuilder> {
    fun joinToCamelCase(strings: Set<String>): String {
        val list = strings.toList()
        val capitalizedStrings = list.mapIndexed { index, str ->
            if (index == 0) str else str.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
        val joinedString = capitalizedStrings.joinToString("")
        return joinedString.replaceFirstChar { it.lowercase(Locale.ROOT) }
    }

    val options = buildList {
        for (variant in variants) {
            val options = variant[optionsKey]
            add(buildList {
                var default: String? = null
                var addDefault = true
                for (option in options) {
                    // add default if all dependencies are friends
                    if (option[isDefaultKey]) {
                        default = option[nameKey]
                    } else {
                        add(
                            option[nameKey] ?: parseError("Name is required for variant option")
                        )

                        if (option[dependsOnKey].any { it.getStringValue("kind") != "friend" }) {
                            addDefault = false
                        }
                    }
                }

                if (addDefault) {
                    add(default!!)
                }
            })
        }
    }

    val cartesian = cartesianSets(options)

    val fragmentBuilderList = this
    buildList {
        for (cartesianElement in cartesian) {
            for (platform in platforms) {
                fragmentBuilderList
                    .filter { it.variants == cartesianElement }
                    .firstOrNull { it.platforms == setOf(platform) }
                    ?.let {
                        add(it)
                    }
            }
        }
    }


    val leafFragments =
        cartesian.flatMap { cartesianElement -> filter { it.variants == cartesianElement }.filter { it.platforms.size == 1 } }

    return when {
        !productType.isLibrary() -> leafFragments
            .map { fragment ->
                fragment.isLeaf = true
                ArtifactBuilder(
                    fragment.name,
                    fragment.platforms,
                    fragment.variants,
                    mutableListOf(fragment)
                )
            }

        else -> {
            leafFragments
                .groupBy { it.variants }
                .entries
                .map {
                    val groupVariants = it.key
                    val fragments = it.value
                    fragments.forEach { fragment -> fragment.isLeaf = true }
                    ArtifactBuilder(
                        joinToCamelCase(groupVariants.toSet()),
                        fragments.flatMap { it.platforms }.toSet(),
                        groupVariants,
                        fragments.toMutableList()
                    )
                }
        }
    }
}

private fun MutableList<FragmentBuilder>.addFragment(fragment: FragmentBuilder, platforms: Set<Platform>) {
    forEach {
        if (platforms.containsAll(it.platforms)) {
            val alreadyExistsTransitively = it.dependencies.any {
                fragment.platforms.containsAll(it.target.platforms)
            }
            if (!alreadyExistsTransitively) {
                it.addDependency(
                    MutableFragmentDependency(
                        fragment, MutableFragmentDependency.DependencyKind.Refines
                    )
                )
            }
        }
    }
    add(fragment)
}

context (Map<String, Set<Platform>>, BuildFileAware)
internal fun List<FragmentBuilder>.handleSettings(config: Settings) {
    config.handleFragmentSettings<Settings>(this, "settings") {
        kotlin = KotlinPartBuilder {
            it.getValue<Settings>("kotlin")?.let { kotlinSettings ->
                // Special
                kotlinSettings.getValue<Double>("languageVersion") {
                    languageVersion = KotlinVersion.requireFromString(it.toString())
                }
                kotlinSettings.getValue<Double>("apiVersion") {
                    apiVersion = KotlinVersion.requireFromString(it.toString())
                }

                // Boolean
                kotlinSettings.getValue<Boolean>("allWarningsAsErrors") { allWarningsAsErrors = it }
                kotlinSettings.getValue<Boolean>("suppressWarnings") { suppressWarnings = it }
                kotlinSettings.getValue<Boolean>("verbose") { verbose = it }
                kotlinSettings.getValue<Boolean>("debug") { debug = it }
                kotlinSettings.getValue<Boolean>("progressiveMode") { progressiveMode = it }

                // Lists
                kotlinSettings.getValue<List<String>>("languageFeatures") { languageFeatures.addAll(it) }
                kotlinSettings.getValue<List<String>>("optIns") { optIns.addAll(it) }
                kotlinSettings.getValue<List<String>>("freeCompilerArgs") { freeCompilerArgs.addAll(it) }
            }
        }

        junit = JunitPartBuilder {
            it.getValue<Settings>("junit")?.let { testSettings ->
                platformEnabled = testSettings.getValue<Boolean>("platformEnabled")
            }
        }
    }
}

context (Map<String, Set<Platform>>, BuildFileAware)
internal fun List<ArtifactBuilder>.handleSettings(
    config: Map<String, Any>,
    fragments: List<FragmentBuilder>
) {
    config.handleArtifactSettings<Settings>(fragments, "settings") {
        android = AndroidPartBuilder {
            it.getValue<Settings>("android")?.let { androidSettings ->
                compileSdkVersion = androidSettings.getStringValue("compileSdkVersion")
                minSdk = androidSettings.getStringValue("minSdk")
                minSdkPreview = androidSettings.getStringValue("minSdkPreview")
                maxSdk = androidSettings.getValue<Int>("maxSdk")
                targetSdk = androidSettings.getStringValue("targetSdk")
                applicationId = androidSettings.getStringValue("applicationId")
                namespace = androidSettings.getStringValue("namespace")
            }
        }

        publishing = PublishingPartBuilder {
            it.getValue<Settings>("publishing")?.let { publishSettings ->
                group = publishSettings.getStringValue("group")
                version = publishSettings.getValue<Any>("version").toString()
            }
        }

        java = JavaPartBuilder {
            it.getValue<Settings>("java")?.let { javaSettings ->
                mainClass = javaSettings.getStringValue("mainClass")
                packagePrefix = javaSettings.getStringValue("packagePrefix")
                target = javaSettings.getStringValue("target")
                source = javaSettings.getStringValue("source")
            }
        }

        compose = ComposePartBuilder {
            it.getByPath<Boolean>("compose", "enabled")?.let {
                enabled = it
            }
        }
    }
}

context (BuildFileAware, Settings)
internal fun List<FragmentBuilder>.calculateSrcDir(platforms: Set<Platform>) {

    val defaultOptions = defaultOptionMap.values.toSet()
    val nonStdOptions = optionMap.filter { it.value.getStringValue("dimension") != "mode" }.keys

    for (fragment in this) {
        val options = fragment.variants.filter { nonStdOptions.contains(it) }.toSet()
        val postfix = buildString {
            val optionsWithoutDefault = options.filter { !defaultOptions.contains(it) }

            if (fragment.platforms != platforms || optionsWithoutDefault.isNotEmpty()) {
                append("@")
            }

            if (fragment.platforms != platforms) {
                if (fragment.alias != null) {
                    append("${fragment.alias}")
                } else {
                    append(fragment.platforms.map { with(mapOf<String, Set<Platform>>()) { setOf(it).toCamelCaseString().first } }
                        .joinToString("+"))
                }
            }

            if (optionsWithoutDefault.isNotEmpty()) {
                if (fragment.platforms != platforms) {
                    append("+")
                }
                append(optionsWithoutDefault.joinToString("+"))
            }
        }

        val srcDir = buildFile.parent
        if (fragment.isTest) {
            fragment.src = srcDir.resolve("test$postfix")
            fragment.resourcesPath = srcDir.resolve("testResources$postfix")
        } else {
            fragment.src = srcDir.resolve("src$postfix")
            fragment.resourcesPath = srcDir.resolve("resources$postfix")
        }
    }
}