package com.tianyisoft.database.relations

import com.tianyisoft.database.Builder

open class BelongsToMany(
    val table: String,
    val pivotTable: String,
    val foreignPivotKey: String,
    val relatedPivotKey: String,
    val localKey: String = "id",
    val relatedKey: String = "id"
): Builder(), Relation {
    val wherePivots: MutableList<List<Any?>> = mutableListOf()
    val wherePivotBetweens: MutableList<List<Any?>> = mutableListOf()
    val wherePivotIns: MutableList<List<Any?>> = mutableListOf()
    val wherePivotNulls: MutableList<List<Any?>> = mutableListOf()
    val orderBys: MutableList<List<Any?>> = mutableListOf()

    open fun wherePivot(column: String, operator: String? = null, value: Any? = null, boolean: String = "and"): BelongsToMany {
        wherePivots.add(listOf(column, operator, value, boolean))
        return where(qualifyPivotColumn(column), operator, value, boolean) as BelongsToMany
    }

    open fun orWherePivot(column: String, operator: String? = null, value: Any? = null): BelongsToMany {
        return wherePivot(qualifyPivotColumn(column), operator, value, "or") as BelongsToMany
    }

    open fun wherePivotBetween(column: String, values: List<Any?>, boolean: String = "and", not: Boolean = false): BelongsToMany {
        wherePivotBetweens.add(listOf(column, values, boolean, not))
        return whereBetween(qualifyPivotColumn(column), values, boolean, not) as BelongsToMany
    }

    open fun orWherePivotBetween(column: String, values: List<Any?>): BelongsToMany {
        return wherePivotBetween(column, values, "or")
    }

    open fun wherePivotNotBetween(column: String, values: List<Any?>, boolean: String = "and"): BelongsToMany {
        return wherePivotBetween(column, values, boolean, true)
    }

    open fun orWherePivotNotBetween(column: String, values: List<Any?>): BelongsToMany {
        return wherePivotNotBetween(column, values, "or")
    }

    open fun wherePivotIn(column: String, values: Any, boolean: String = "and", not: Boolean = false): BelongsToMany {
        wherePivotIns.add(listOf(column, values, boolean, not))
        return whereIn(qualifyPivotColumn(column), values, boolean, not) as BelongsToMany
    }

    open fun orWherePivotIn(column: String, values: Any): BelongsToMany {
        return wherePivotIn(column, values, "or")
    }

    open fun wherePivotNotIn(column: String, values: Any, boolean: String = "and"): BelongsToMany {
        return wherePivotIn(column, values, boolean, true)
    }

    open fun orWherePivotNotIn(column: String, values: Any): BelongsToMany {
        return wherePivotNotIn(column, values, "or")
    }

    open fun wherePivotNull(column: String, boolean: String = "and", not: Boolean = false): BelongsToMany {
        wherePivotNulls.add(listOf(column, boolean, not))
        return whereNull(qualifyPivotColumn(column), boolean, not) as BelongsToMany
    }

    open fun orWherePivotNull(column: String): BelongsToMany {
        return wherePivotNull(column, "or")
    }

    open fun wherePivotNotNull(column: String, boolean: String = "and"): BelongsToMany {
        return wherePivotNull(column, boolean, true)
    }

    open fun orWherePivotNotNull(column: String): BelongsToMany {
        return wherePivotNotNull(column, "or")
    }

    open fun orderByPivot(column: String, direction: String = "asc"): BelongsToMany {
        orderBys.add(listOf(column, direction))
        return orderBy(qualifyPivotColumn(column), direction) as BelongsToMany
    }

    open fun qualifyPivotColumn(column: String): String {
        if (column.contains(".")) {
            return column
        }
        return "$pivotTable.$column"
    }
}
