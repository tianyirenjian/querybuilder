package com.tianyisoft.database.util

import com.tianyisoft.database.Table
import com.tianyisoft.database.exceptions.InvalidArgumentException
import java.lang.reflect.Field
import java.text.SimpleDateFormat
import java.util.*
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

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
        is Number -> obj.toDouble().compareTo(0) == 0
        else -> false
    }
}

fun Any?.toBool(): Boolean {
    return when(val value = this) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble().compareTo(0) != 0
        is String -> !(value == "" || value == "0" || value.lowercase() == "false")
        is Collection<*> -> value.isNotEmpty()
        is Array<*> -> value.isNotEmpty()
        is Map<*, *> -> value.isNotEmpty()
        else -> true
    }
}

fun classToMapForBuilder(obj: Any): Map<String, Any?> {
    if (obj !is Table) {
        throw InvalidArgumentException("object should be instance of com.tianyisoft.database.Table")
    }
    val fields = getAllFields(obj.javaClass).filter { it.name in obj.fillable().keys }
    val map = hashMapOf<String, Any?>()
    fields.forEach {
        val name = obj.fillable()[it.name]!!
        val accessible = it.isAccessible
        it.isAccessible = true
        map[name] = it.get(obj)
        it.isAccessible = accessible
    }
    return map
}

fun getAllFields(obj: Class<*>): List<Field> {
    return if (obj.superclass == null) {
        getAllFields(obj.superclass) + obj.declaredFields.toList()
    } else {
        obj.declaredFields.toList()
    }
}
