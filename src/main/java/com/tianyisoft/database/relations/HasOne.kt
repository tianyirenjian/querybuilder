package com.tianyisoft.database.relations

open class HasOne(
    override val table: String,
    override val foreignKey: String,
    override val localKey: String = "id"
) : HasMany(table, foreignKey, localKey) {
    override var recursive = false
    override fun clone(): Any {
        val hasOne = HasOne(table, foreignKey, localKey)
        hasOne.recursive = recursive
        copyAttributes(hasOne)
        return hasOne
    }

    override fun copy(): HasOne {
        return clone() as HasOne
    }
}
