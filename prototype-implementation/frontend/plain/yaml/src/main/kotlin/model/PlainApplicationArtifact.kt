package org.jetbrains.deft.proto.frontend.model

import org.jetbrains.deft.proto.frontend.*

context (Stateful<FragmentBuilder, Fragment>)
internal class PlainApplicationArtifact(
    private val fragmentBuilders: List<FragmentBuilder>,
    private val platform: Platform,
    private val cartesianElement: List<String>
) : Artifact {
    private val targetInternalFragment = fragmentBuilders.filter { it.platforms == setOf(platform) }
        .firstOrNull { it.variants == cartesianElement.toSet() } ?: error("Something went wrong")

    override val name: String
        // TODO Handle the case, when there are several artifacts with same name. Can it be?
        // If it can't - so it should be expressed in API via sealed interface.
        // FIXME
        get() = targetInternalFragment.name
    override val fragments: List<Fragment>
        get() = listOf(targetInternalFragment.build())
    override val platforms: Set<Platform>
        get() = setOf(platform)
    override val parts: ClassBasedSet<ArtifactPart<*>>
        get() {
            return buildSet {
                if (platform == Platform.ANDROID) {
                    targetInternalFragment.android?.let {
                        add(ByClassWrapper(AndroidArtifactPart(it.compileSdkVersion)))
                    }
                }

                targetInternalFragment.java?.let {
                    add(ByClassWrapper(JavaApplicationArtifactPart(it.mainClass, it.packagePrefix)))
                }

                targetInternalFragment.native?.let {
                    add(
                        ByClassWrapper(
                            NativeApplicationArtifactPart(it.entryPoint)
                        )
                    )
                }
            }
        }
}