package com.tianyisoft.database.relations

data class HasMany(
    override val table: String,
    override val foreignKey: String,
    override val localKey: String = "id"
) : HasOne(table, foreignKey, localKey)
