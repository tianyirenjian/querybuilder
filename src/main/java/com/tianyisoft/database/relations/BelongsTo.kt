package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class BelongsTo(
    val table: String,
    val foreignKey: String,
    val ownerKey: String = "id"
): Builder(), Relation {
    override fun clone(): Any {
        val belongsTo = BelongsTo(table, foreignKey, ownerKey)
        copyAttributes(belongsTo)
        return belongsTo
    }

    override fun copy(): BelongsTo {
        return clone() as BelongsTo
    }
}
