package com.tianyisoft.querybuilder.util

import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.Collection
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

fun Date.format(format: String = "yyyy-MM-dd HH:mm:ss"): String = SimpleDateFormat(format).format(this)

fun flatten(value: Any?): List<Any?> {
    if (value == null) {
        return listOf(null)
    }
    val values = mutableListOf<Any?>()
    when (value) {
        is Map<*, *> -> {
            value.forEach { _, v ->
                when (v) {
                    is Map<*, *>, is List<*> -> values.addAll(flatten(v))
                    else -> values.add(v)
                }
            }
        }
        is List<*> -> {
            value.forEach { v ->
                when (v) {
                    is Map<*, *>, is List<*> -> values.addAll(flatten(v))
                    else -> values.add(v)
                }
            }
        }
        else -> values.add(value)
    }
    return values
}

@Suppress("UNCHECKED_CAST")
fun <R> readInstanceProperty(instance: Any, propertyName: String): R {
    val property = instance::class.memberProperties.first { it.name == propertyName } as KProperty1<Any, *>
    property.isAccessible = true
    return property.get(instance) as R
}

fun empty(obj: Any?): Boolean {
    if (obj == null) return true
    return when(obj) {
        is List<*> -> obj.isEmpty()
        is Map<*, *> -> obj.isEmpty()
        is Set<*> -> obj.isEmpty()
        is String -> obj.length == 0
        is Collection<*> -> obj.isEmpty()
        is Array<*> -> obj.size == 0
        is Number -> obj == 0
        else -> false
    }
}

fun wrapListString(values: Any): List<String> {
    return when(values) {
        is String -> listOf(values)
        is List<*> -> values.map { if (it is String) it else it.toString() }
        is Array<*> -> values.map { if (it is String) it else it.toString()  }
        is Set<*> -> values.map { if (it is String) it else it.toString()  }
        else -> listOf(values.toString())
    }
}

fun Any?.toBool(): Boolean {
    return when(val value = this) {
        null -> false
        is Boolean -> value
        is Number -> value != 0
        is String -> !(value == "" || value == "0" || value.lowercase() == "false")
        is Collection<*> -> value.isNotEmpty()
        is Array<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }
}
