package com.tianyisoft.database.relations

open class HasOne(
    open val table: String,
    open val foreignKey: String,
    open val localKey: String = "id"
) : Relation