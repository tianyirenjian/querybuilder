package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class HasOne(
    open val table: String,
    open val foreignKey: String,
    open val localKey: String = "id"
) : Builder(), Relation