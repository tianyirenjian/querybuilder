package com.tianyisoft.database.relations

data class HasMany(
    override val table: String,
    override val foreignKey: String,
    override val localKey: String = "id"
) : HasOne(table, foreignKey, localKey) {
    override fun clone(): Any {
        val hasMany = HasMany(table, foreignKey, localKey)
        copyAttributes(hasMany)
        return hasMany
    }

    override fun copy(): HasMany {
        return clone() as HasMany
    }
}
