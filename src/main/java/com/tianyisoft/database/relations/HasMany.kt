package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class HasMany(
    open val table: String,
    open val foreignKey: String,
    open val localKey: String = "id"
) : Builder(), Relation {
    override fun clone(): Any {
        val hasMany = HasMany(table, foreignKey, localKey)
        copyAttributes(hasMany)
        return hasMany
    }

    override fun copy(): HasMany {
        return clone() as HasMany
    }
}
