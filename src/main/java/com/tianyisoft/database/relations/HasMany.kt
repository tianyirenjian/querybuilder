package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class HasMany(
    open val table: String,
    open val foreignKey: String,
    open val localKey: String = "id"
) : Builder(), Relation {
    open var recursive = false

    fun withRecursive(recursive: Boolean = true): HasMany {
        this.recursive = recursive
        return this
    }

    override fun clone(): Any {
        val hasMany = HasMany(table, foreignKey, localKey)
        hasMany.recursive = recursive
        copyAttributes(hasMany)
        return hasMany
    }

    override fun copy(): HasMany {
        return clone() as HasMany
    }
}
