package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class HasOne(
    open val table: String,
    open val foreignKey: String,
    open val localKey: String = "id"
) : Builder(), Relation {
    override fun clone(): Any {
        val hasOne = HasOne(table, foreignKey, localKey)
        copyAttributes(hasOne)
        return hasOne
    }

    override fun copy(): HasOne {
        return clone() as HasOne
    }
}
