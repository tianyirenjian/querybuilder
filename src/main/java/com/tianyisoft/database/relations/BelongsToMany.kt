package com.tianyisoft.database.relations

data class BelongsToMany(
    val table: String,
    val pivotTable: String,
    val foreignPivotKey: String,
    val relatedPivotKey: String,
    val localKey: String = "id",
    val relatedKey: String = "id"
): Relation
