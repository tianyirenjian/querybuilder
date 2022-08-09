package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

data class BelongsTo(
    val table: String,
    val foreignKey: String,
    val ownerKey: String = "id"
): Builder(), Relation
