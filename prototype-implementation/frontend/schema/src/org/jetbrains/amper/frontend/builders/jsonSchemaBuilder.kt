/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.builders

import org.jetbrains.amper.frontend.api.CustomSchemaDef
import java.io.Writer
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation


/**
 * The place where schema is actually built.
 */
class JsonSchemaBuilderCtx {
    val visited = mutableSetOf<KClass<*>>()
    val customSchemaDef = mutableMapOf<String, String>()
    val declaredPatternProperties: MutableMap<String, MutableList<String>> = mutableMapOf()
    val declaredProperties: MutableMap<String, MutableList<String>> = mutableMapOf()
}

/**
 * A visitor, that traverses the tree and put all schema info into [JsonSchemaBuilderCtx].
 */
class JsonSchemaBuilder(
    private val currentRoot: KClass<*>,
    private val ctx: JsonSchemaBuilderCtx,
) : RecurringVisitor() {
    companion object {
        fun writeSchema(
            root: KClass<*>,
            w: Writer,
            schemaId: String = "${root.simpleName}.json",
            title: String = "${root.simpleName} schema",
        ) = JsonSchemaBuilder(root, JsonSchemaBuilderCtx())
            .apply { visitClas(root) }
            .ctx.run {
                w.apply {
                    appendLine("{")
                    appendLine("  \"\$schema\": \"https://json-schema.org/draft/2020-12/schema\",")
                    appendLine("  \"\$id\": \"$schemaId\",")
                    appendLine("  \"title\": \"$title\",")
                    appendLine("  \"type\": \"object\",")

                    appendLine("  \"allOf\": [")
                    appendLine("    {")
                    appendLine("      ${root.asReferenceTo}")
                    appendLine("    }")
                    appendLine("  ],")

                    appendLine("  \"\$defs\": {")


                    visited.forEachEndAware { isEnd, it ->
                        val key = it.jsonDef
                        val propertyValues = declaredProperties[key]
                        val patternProperties = declaredPatternProperties[key]
                        appendLine("    \"$key\": {")

                        val customSchema = customSchemaDef[key]
                        if (customSchema != null) {
                            appendLine(customSchema.prependIndent("      "))
                        } else {
                            appendLine("      \"type\": \"object\",")

                            // pattern properties section.
                            if (patternProperties != null) {
                                appendLine("      \"patternProperties\": {")
                                patternProperties.forEachEndAware { isEnd2, it ->
                                    append(it.replaceIndent("        "))
                                    if (!isEnd2) appendLine(",") else appendLine()
                                }
                                if (propertyValues != null) appendLine("      },")
                                else appendLine("      }")
                            }

                            // properties section.
                            if (propertyValues != null) {
                                appendLine("      \"properties\": {")
                                propertyValues.forEachEndAware { isEnd2, it ->
                                    append(it.replaceIndent("        "))
                                    if (!isEnd2) appendLine(",") else appendLine()
                                }
                                appendLine("      }")
                            }
                        }

                        if (!isEnd) appendLine("    },")
                        else appendLine("    }")
                    }
                    appendLine("  }")
                    appendLine("}")
                }
            }
    }

    private fun addPatternProperty(prop: KProperty<*>, block: () -> String) {
        ctx.declaredPatternProperties.compute(currentRoot.jsonDef) { _, old ->
            old.orNew.apply { add(block()) }
        }
    }

    private fun addProperty(prop: KProperty<*>, block: () -> String) {
        ctx.declaredProperties.compute(currentRoot.jsonDef) { _, old ->
            old.orNew.apply { add(buildProperty(prop.name, block)) }
        }
    }

    override fun visitClas(klass: KClass<*>) = if (ctx.visited.add(klass)) {
        when {
            klass.hasAnnotation<CustomSchemaDef>() ->
                ctx.customSchemaDef[klass.jsonDef] = klass.findAnnotation<CustomSchemaDef>()!!.json.trimIndent()
            else ->
                visitSchema(klass, JsonSchemaBuilder(klass, ctx))
        }
    } else Unit

    override fun visitTyped(
        prop: KProperty<*>,
        type: KType,
        schemaNodeType: KType,
        types: Collection<KClass<*>>,
        modifierAware: Boolean
    ) {
        fun buildForTyped(type: KType, firstInvoke: Boolean = false): String = when {
            type.isSchemaNode -> types.wrapInAnyOf { it.asReferenceTo }
            type.isCollection -> buildSchemaCollection { buildForTyped(type.collectionType) }
            // TODO Support modifiers.
            type.isMap && firstInvoke && modifierAware -> buildModifierBasedCollection(prop.name) { buildForTyped(type.mapValueType) }
            type.isMap -> buildSchemaKeyBasedCollection { buildForTyped(type.mapValueType) }
            else -> error("Unsupported type $type") // TODO Report
        }

        // Modifier aware properties are always pattern properties.
        if (modifierAware) addPatternProperty(prop) { buildForTyped(type, true) }
        else addProperty(prop) { buildForTyped(type, true) }
        super.visitTyped(prop, type, schemaNodeType, types, modifierAware)
    }

    override fun visitCommon(prop: KProperty<*>, type: KType, default: Any?) =
        addProperty(prop) { buildForScalarBased(type) }

    private fun buildForScalarBased(type: KType): String = when {
        type.isScalar -> buildScalar(type)
        type.isCollection -> buildSchemaCollection { buildForScalarBased(type.collectionType) }
        type.isMap -> buildSchemaKeyBasedCollection { buildForScalarBased(type.mapValueType) }
        else -> error("Unsupported type $type") // TODO Report
    }

    private fun buildScalar(type: KType) = when {
        type.isEnum -> type.enumSchema
        type.isString || type.isTraceableString -> stringSchema
        type.isBoolean -> booleanSchema
        type.isPath -> stringSchema
        type.isInt -> TODO()
        else -> error("Unsupported type") // TODO reporting
    }

    private val MutableList<String>?.orNew get() = this ?: mutableListOf()
}