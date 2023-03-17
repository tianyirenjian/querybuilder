package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class BelongsTo(
    val table: String,
    val foreignKey: String,
    val ownerKey: String = "id"
): Builder(), Relation {
    open var recursive = false

    @JvmOverloads
    fun withRecursive(recursive: Boolean = true): BelongsTo {
        this.recursive = recursive
        return this
    }
    override fun clone(): Any {
        val belongsTo = BelongsTo(table, foreignKey, ownerKey)
        belongsTo.recursive = recursive
        copyAttributes(belongsTo)
        return belongsTo
    }

    override fun copy(): BelongsTo {
        return clone() as BelongsTo
    }
}
