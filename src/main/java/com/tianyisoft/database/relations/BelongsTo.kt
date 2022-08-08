package com.tianyisoft.database.relations

data class BelongsTo(
    val table: String,
    val foreignKey: String,
    val ownerKey: String = "id"
): Relation
