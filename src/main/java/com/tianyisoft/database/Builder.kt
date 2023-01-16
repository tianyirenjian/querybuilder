package com.tianyisoft.database

import com.tianyisoft.database.enums.DeletedDataType
import com.tianyisoft.database.exceptions.InvalidArgumentException
import com.tianyisoft.database.exceptions.MultipleRecordsFoundException
import com.tianyisoft.database.exceptions.RecordsNotFoundException
import com.tianyisoft.database.exceptions.UnsupportedOperatorException
import com.tianyisoft.database.grammar.Grammar
import com.tianyisoft.database.grammar.MysqlGrammar
import com.tianyisoft.database.jackson.JsonBuilder
import com.tianyisoft.database.relations.*
import com.tianyisoft.database.snippets.Snippet
import com.tianyisoft.database.util.*
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import java.util.*
import kotlin.collections.LinkedHashMap
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.full.memberFunctions


open class Builder: Cloneable {
    private val log = LoggerFactory.getLogger(Builder::class.java)
    var from: Any? = null
    var limit: Int? = null
    var unionLimit: Int? = null
    var offset: Int? = null
    var unionOffset: Int? = null
    val wheres:MutableList<Map<String, Any?>> = mutableListOf()
    val unions:MutableList<Map<String, Any?>> = mutableListOf()
    val joins:MutableList<JoinClause> = mutableListOf()
    val groups: MutableList<Any> = mutableListOf()
    val havings:MutableList<Map<String, Any?>> = mutableListOf()
    var orders:MutableList<Map<String, Any?>> = mutableListOf()
    val unionOrders:MutableList<Map<String, Any?>> = mutableListOf()
    var aggregate: MutableMap<String, Any?> = mutableMapOf()
    var distinct: Boolean = false
    var columns: MutableList<Any> = mutableListOf()
    val bindings: LinkedHashMap<String, MutableList<Any?>> = linkedMapOf() // order is important
    val withes: MutableMap<String, Map<String, Any?>> = mutableMapOf()

    var jdbcTemplate:JdbcTemplate? = null
    var grammar: Grammar = MysqlGrammar()

    private var softDelete = false
    private var deletedColumn = "deleted_at"
    private var deletedDataType = DeletedDataType.DATETIME

    private val operators = listOf(
        "=", "<", ">", "<=", ">=", "<>", "!=", "<=>",
        "like", "like binary", "not like", "ilike",
        "&", "|", "^", "<<", ">>",
        "rlike", "not rlike", "regexp", "not regexp",
        "~", "~*", "!~", "!~*", "similar to",
        "not similar to", "not ilike", "~~*", "!~~*",
    )

    init {
        listOf("select", "from", "join", "where", "groupBy", "having", "order", "union", "unionOrder").forEach {
            bindings[it] = mutableListOf()
        }
    }

    @JvmOverloads
    open fun enableSoftDelete(column: String = "deleted_at", dataType: DeletedDataType = DeletedDataType.DATETIME): Builder {
        softDelete = true
        deletedColumn = column
        deletedDataType = dataType
        when (dataType) {
            DeletedDataType.DATETIME -> {
                whereNull(column)
            }
            DeletedDataType.INTEGER -> {
                where(column, "=", 0)
            }
        }
        return this
    }

    open fun select(vararg fields: Any): Builder {
        columns = if (fields.isEmpty()) mutableListOf("*") else fields.toMutableList()
        bindings["select"] = mutableListOf()
        return this
    }

    @JvmOverloads
    open fun selectRaw(fields: String, bindings: List<Any?> = listOf()): Builder {
        columns.add(Expression(fields))
        addBinding(bindings, "select")
        return this
    }

    open fun addSelect(vararg fields: Any): Builder {
        fields.forEach {
            columns.add(it)
        }
        return this
    }

    open fun selectSub(queryOrClosure: Any, alias: String): Builder {
        val sub = createSub(queryOrClosure)
        return selectRaw("(${sub.first}) as ${grammar.wrapTable(alias)}", sub.second)
    }

    @JvmOverloads
    open fun distinct(boolean: Boolean = true): Builder {
        distinct = boolean
        return this
    }

    @JvmOverloads
    open fun from(table: Any?, alias: String? = null): Builder {
        if (table is Builder || table is Function1<*, *>) {
            if (alias == null) {
                throw InvalidArgumentException("alias must not be null")
            }
            return fromSub(table, alias)
        }
        from = if (alias == null) table else "$table as $alias"
        return this
    }

    @JvmOverloads
    open fun table(table: String, alias: String? = null): Builder {
        return from(table, alias)
    }

    @JvmOverloads
    open fun fromRaw(expression: String, bindings: List<Any?> = listOf()): Builder {
        from = Expression(expression)
        addBinding(bindings, "from")
        return this
    }
    open fun fromSub(queryOrClosure: Any, alias: String): Builder {
        val sub = createSub(queryOrClosure)
        return fromRaw("(${sub.first}) as ${grammar.wrapTable(alias)}", sub.second)
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun createSub(queryOrClosure: Any): Pair<String, List<Any?>> {
        var query = queryOrClosure
        if (queryOrClosure is Function1<*, *>) {
            queryOrClosure as Function1<Builder, Unit>
            query = forSubQuery()
            queryOrClosure(query)
        }
        return parseSub(query)
    }

    protected open fun parseSub(query: Any): Pair<String, List<Any?>> {
        return when (query) {
            is Builder -> Pair(query.toSql(), query.getFlattenBindings())
            is String -> Pair(query, listOf())
            else -> throw InvalidArgumentException("A subquery must be a query builder instance, a Closure, or a string.")
        }
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun join(table: Any, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner", where: Boolean = false): Builder {
        val join = newJoinClause(this, type, table)
        if (firstOrClosure is Function1<*, *>) {
            firstOrClosure as Function1<JoinClause, Unit>
            firstOrClosure(join)
            joins.add(join)
            addBinding(join.getFlattenBindings(), "join")
        } else {
            if (where) {
                joins.add(join.where(firstOrClosure, operator!!, second!!) as JoinClause)
            } else {
                joins.add(join.on(firstOrClosure, operator, second))
            }
            addBinding(join.getFlattenBindings(), "join")
        }
        return this
    }

    @JvmOverloads
    open fun joinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner") =
        join(table, firstOrClosure, operator, second, type, true)

    @JvmOverloads
    open fun joinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner", where: Boolean = false): Builder {
        val sub = createSub(queryOrClosure)
        val expression = Expression("(${sub.first}) as ${grammar.wrapTable(alias)}")
        addBinding(sub.second, "join")
        return join(expression, firstOrClosure, operator, second, type, where)
    }

    @JvmOverloads
    open fun leftJoin(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return join(table, firstOrClosure, operator, second, "left")
    }

    @JvmOverloads
    open fun leftJoinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return joinWhere(table, firstOrClosure, operator, second, "left")
    }

    @JvmOverloads
    open fun leftJoinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null) =
        joinSub(queryOrClosure, alias, firstOrClosure, operator, second, "left")

    @JvmOverloads
    open fun rightJoin(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return join(table, firstOrClosure, operator, second, "right")
    }

    @JvmOverloads
    open fun rightJoinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return joinWhere(table, firstOrClosure, operator, second, "right")
    }

    @JvmOverloads
    open fun rightJoinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null) =
        joinSub(queryOrClosure, alias, firstOrClosure, operator, second, "right")


    @JvmOverloads
    open fun crossJoin(table: String, firstOrClosure: Any? = null, operator: String? = null, second: String? = null): Builder {
        if (firstOrClosure != null) {
            return join(table, firstOrClosure, operator!!, second!!, "cross")
        }
        joins.add(newJoinClause(this, "cross", table))
        return this
    }
    protected open fun newJoinClause(query: Builder, type: String, table: Any): JoinClause {
        return JoinClause(query, type, table)
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun where(column: Any, operator: String? = null, value: Any? = null, boolean: String = "and"): Builder {
        if (column is List<*>) {
            return addListOfWheres(column as List<List<Any?>>, boolean)
        }
        if (column is Map<*, *>) {
            return addMapOfWheres(column as Map<String, Any?>, boolean)
        }

        if (column is Function1<*, *> && operator == null) {
            return whereNested(column as Function1<Builder, Unit>, boolean)
        }

        if ((column is Function1<*, *> || column is Builder) && operator != null) {
            val sub = createSub(column)
            return addBinding(sub.second, "where").where(Expression("(${sub.first})"), operator, value, boolean)
        }

        if (value is Function1<*, *>) {
            return whereSub(column as String, operator, value as Function1<Builder, Unit>, boolean)
        }

        if (value == null) {
            return whereNull(column, boolean, operator != "=")
        }
        wheres.add(hashMapOf(
            "type" to "Basic",
            "column" to column,
            "operator" to operator,
            "value" to value,
            "boolean" to boolean
        ))
        if (value !is Expression) {
            addBinding(flatten(value).first(), "where")
        }
        return this
    }

    @JvmOverloads
    open fun orWhere(column: Any, operator: String? = null, value: Any? = null) = where(column, operator, value, "or")

    @JvmOverloads
    open fun whereNot(column: Any, operator: String? = null, value: Any? = null, boolean: String = "and"): Builder {
        return where(column, operator, value, "$boolean not")
    }

    @JvmOverloads
    open fun orWhereNot(column: Any, operator: String? = null, value: Any? = null): Builder {
        return whereNot(column, operator, value, "or")
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun whereColumn(first: Any, operator: String? = null, second: String? = null, boolean: String = "and"): Builder {
        if (first is List<*>) {
            return this.addListOfWheres(first as List<List<Any?>>, boolean, "whereColumn")
        }
        if (first is Map<*, *>) {
            return this.addMapOfWheres(first as Map<String, Any?>, boolean, "whereColumn")
        }

        wheres.add(hashMapOf(
            "type" to "Column",
            "first" to first,
            "operator" to operator,
            "second" to second,
            "boolean" to boolean
        ))
        return this
    }

    open fun orWhereColumn(first: String, operator: String, second: String) = whereColumn(first, operator, second, "or")

    @JvmOverloads
    open fun whereNull(columns: Any, boolean: String = "and", not: Boolean = false): Builder {
        if (columns !is String && columns !is List<*> && columns !is Array<*> && columns !is Set<*>) {
            throw InvalidArgumentException("columns must be String or List<String> or Array<String> or Set<String")
        }
        wrapList(columns).forEach {
            wheres.add(hashMapOf(
                "type" to if (not) "NotNull" else "Null",
                "column" to it,
                "boolean" to boolean
            ))
        }
        return this
    }

    @JvmOverloads
    open fun whereNotNull(column: Any, boolean: String = "and") = whereNull(column, boolean, true)
    open fun orWhereNull(column: Any) = whereNull(column, "or")
    open fun orWhereNotNull(column: Any) = whereNotNull(column, "or")

    private fun wrapList(values: Any): List<String> {
        return when(values) {
            is String -> listOf(values)
            is Collection<*> -> values.map { if (it is String) it else it.toString() }
            is Array<*> -> values.map { if (it is String) it else it.toString()  }
            else -> listOf(values.toString())
        }
    }

    @JvmOverloads
    open fun whereRaw(sql: String, bindings: List<Any?>, boolean: String = "and"): Builder {
        wheres.add(hashMapOf(
            "type" to "Raw",
            "sql" to sql,
            "boolean" to boolean
        ))
        addBinding(bindings)
        return this
    }

    open fun orWhereRaw(sql: String, bindings: List<Any?>) = whereRaw(sql, bindings, "or")

    @JvmOverloads
    open fun whereIn(column: String, values: Any, boolean: String = "and", not: Boolean = false): Builder {
        var realValues = values
        if (values is Builder || values is Function1<*, *>) {
            val sub = createSub(values)
            realValues = listOf(Expression(sub.first))
            addBinding(sub.second, "where")
        }
        if (realValues is Collection<*>) {
            realValues = realValues.toList()
        }

        wheres.add(hashMapOf(
            "type" to if (not) "NotIn" else "In",
            "column" to column,
            "values" to realValues,
            "boolean" to boolean
        ))
        addBinding(cleanBindings(realValues as List<Any?>))
        return this
    }

    @JvmOverloads
    open fun whereNotIn(column: String, values: List<Any?>, boolean: String = "and") = whereIn(column, values, boolean, true)
    open fun orWhereIn(column: String, values: List<Any?>) = whereIn(column, values, "or")
    open fun orWhereNotIn(column: String, values: List<Any?>) = whereNotIn(column, values, "or")

    @JvmOverloads
    open fun whereBetween(column: String, values: List<Any?>, boolean: String = "and", not: Boolean = false): Builder {
        wheres.add(hashMapOf(
            "type" to "Between",
            "column" to column,
            "values" to values,
            "boolean" to boolean,
            "not" to not
        ))
        addBinding(cleanBindings(values).subList(0, 2))
        return this
    }

    @JvmOverloads
    open fun whereNotBetween(column: String, values: List<Any?>, boolean: String = "and") = whereBetween(column, values, boolean, true)
    open fun orWhereBetween(column: String, values: List<Any?>) = whereBetween(column, values, "or")
    open fun orWhereNotBetween(column: String, values: List<Any?>) = whereNotBetween(column, values, "or")

    @JvmOverloads
    open fun whereBetweenColumns(column: String, values: List<String>, boolean: String = "and", not: Boolean = false): Builder {
        wheres.add(hashMapOf(
            "type" to "BetweenColumns",
            "column" to column,
            "values" to values,
            "boolean" to boolean,
            "not" to not
        ))
        return this
    }

    @JvmOverloads
    open fun whereDate(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Date", column, operator, value, boolean)
    }
    open fun orWhereDate(column: String, operator: String, value: Any?) = whereDate(column, operator, value, "or")

    @JvmOverloads
    open fun whereTime(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Time", column, operator, value, boolean)
    }

    open fun orWhereTime(column: String, operator: String, value: Any?) = whereTime(column, operator, value, "or")

    @JvmOverloads
    open fun whereDay(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        var value2 = value
        if (value !is Expression) {
            value2 = value?.toString()?.padStart(2, '0')
        }
        return addDateBasedWhere("Day", column, operator, value2, boolean)
    }

    open fun orWhereDay(column: String, operator: String, value: Any?) = whereDay(column, operator, value, "or")

    @JvmOverloads
    open fun whereMonth(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        var value2 = value
        if (value !is Expression) {
            value2 = value?.toString()?.padStart(2, '0')
        }
        return addDateBasedWhere("Month", column, operator, value2, boolean)
    }

    open fun orWhereMonth(column: String, operator: String, value: Any?) = whereMonth(column, operator, value, "or")

    @JvmOverloads
    open fun whereYear(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Year", column, operator, value, boolean)
    }

    open fun orWhereYear(column: String, operator: String, value: Any?) = whereYear(column, operator, value, "or")

    protected open fun addDateBasedWhere(type: String, column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        wheres.add(hashMapOf(
            "type" to type,
            "column" to column,
            "operator" to operator,
            "value" to value,
            "boolean" to boolean
        ))
        if (value !is Expression) {
            addBinding(value)
        }
        return this
    }

    protected open fun whereSub(column: String, operator: String?, sub: (Builder) -> Unit, boolean: String = "and"): Builder {
        val query = forSubQuery()
        sub(query)
        wheres.add(hashMapOf(
            "type" to "Sub",
            "column" to column,
            "operator" to operator,
            "query" to query,
            "boolean" to boolean
        ))
        addBinding(query.getFlattenBindings(), "where")
        return this
    }

    @JvmOverloads
    open fun whereExists(callback: (Builder) -> Unit, boolean: String = "and", not: Boolean = false): Builder {
        val query = forSubQuery()
        callback(query)
        return addWhereExistsQuery(query, boolean, not)
    }

    @JvmOverloads
    open fun whereNotExists(callback: (Builder) -> Unit, boolean: String = "and") = whereExists(callback, boolean, true)
    open fun orWhereExists(callback: (Builder) -> Unit, not: Boolean = false) = whereExists(callback, "or", not)
    open fun orWhereNotExists(callback: (Builder) -> Unit) = whereNotExists(callback, "or")

    protected open fun addWhereExistsQuery(query: Builder, boolean: String = "and", not: Boolean = false): Builder {
        wheres.add(hashMapOf(
            "type" to if (not) "NotExists" else "Exists",
            "query" to query,
            "boolean" to boolean
        ))
        addBinding(query.getFlattenBindings())
        return this
    }

    @JvmOverloads
    open fun whereHas(relation: Relation, operator: String = ">=", count: Int = 1, boolean: String = "and"): Builder {
        if (canUseExists(operator, count)) {
            val sub = buildRelationExistsSub(relation)
            val not = (operator == "<" && count == 1) || (operator in listOf("<", "=", "<=") && count == 0)
            addWhereExistsQuery(sub, boolean, not)
        } else {
            val sub = buildRelationCountQuery(relation)
            where(sub, operator, count, boolean)
        }
        return this
    }

    @JvmOverloads
    open fun orWhereHas(relation: Relation, operator: String = ">=", count: Int = 1): Builder =
        whereHas(relation, operator, count, "or")

    @JvmOverloads
    open fun whereNotHas(relation: Relation, operator: String = ">=", count: Int = 1, boolean: String = "and"): Builder {
        return whereHas(relation, getOppositeOperation(operator), count, boolean)
    }

    @JvmOverloads
    open fun orWhereNotHas(relation: Relation, operator: String = ">=", count: Int = 1): Builder {
        return whereNotHas(relation, operator, count, "or")
    }

    protected open fun getOppositeOperation(operator: String): String {
        return hashMapOf(
            ">" to "<=",
            "<" to ">=",
            "=" to "!=",
            "!=" to "=",
            "<>" to "=",
            ">=" to "<",
            "<=" to ">"
        )[operator] ?: throw UnsupportedOperatorException()
    }

    protected open fun buildRelationCountQuery(relation: Relation): Builder =
        relationBuilder(relation).selectRaw("count(*)")

    protected open fun buildRelationExistsSub(relation: Relation): Builder =
        relationBuilder(relation).selectRaw("1")

    protected open fun canUseExists(operator: String, count: Int): Boolean {
        return (operator in listOf(">=", "<") && count == 1) || (operator in listOf(">", "<", "<=", "=") && count == 0)
    }

    protected open fun forSubQuery(): Builder {
        return newQuery()
    }

    open fun newQuery(): Builder {
        val builder =  Builder()
        builder.jdbcTemplate = jdbcTemplate
        builder.grammar = grammar
        return builder
    }

    protected open fun addMapOfWheres(column: Map<String, Any?>, boolean: String, method: String = "where"): Builder {
        return whereNested({
            column.forEach { (c, v) -> it::class.memberFunctions.first { f -> f.name == method && f.parameters.size == 5 }.call(it, c, "=", v, "and") }
        }, boolean)
    }

    protected open fun addListOfWheres(column: List<List<Any?>>, boolean: String, method: String = "where"): Builder {
        return whereNested({
            val whereFunction = it::class.memberFunctions.first { f -> f.name == method && f.parameters.size == 5 }
            column.forEach{ l ->
                when(l.size) {
                    2 -> whereFunction.call(it, l.component1(), "=", l.component2(), "and")
                    3 -> whereFunction.call(it, l.component1(), l.component2(), l.component3(), "and")
                    4 -> whereFunction.call(it, l.component1(), l.component2(), l.component3(), l.component4())
                    else -> throw InvalidArgumentException("Invalid where parameters count ${l.size}.")
                }
            }
        }, boolean)
    }

    @JvmOverloads
    open fun whereNested(callback: (Builder) -> Unit, boolean: String = "and"): Builder {
        val query = forNestedWhere()
        callback(query)
        return addNestedWhereQuery(query, boolean)
    }

    protected open fun addNestedWhereQuery(query: Builder, boolean: String): Builder {
        if (query.wheres.isNotEmpty()) {
            wheres.add(hashMapOf(
                "type" to "Nested",
                "query" to query,
                "boolean" to boolean
            ))
            addBinding(query.getRawBindings()["where"], "where")
        }
        return this
    }

    open fun use(snippet: Snippet, vararg params: Any?): Builder {
        snippet.apply(this, *params.map { it }.toTypedArray())
        return this
    }

    open fun groupBy(vararg groups: String): Builder {
        this.groups.addAll(groups)
        return this
    }

    @JvmOverloads
    open fun groupByRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
        this.groups.add(Expression(sql))
        addBinding(bindings, "groupBy")
        return this
    }

    @JvmOverloads
    open fun having(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        havings.add(hashMapOf(
            "type" to "Basic",
            "column" to column,
            "operator" to operator,
            "value" to value,
            "boolean" to boolean
        ))
        if (value !is Expression) {
            addBinding(flatten(value).first(), "having")
        }
        return this
    }

    open fun orHaving(column: String, operator: String, value: Any?): Builder {
        return having(column, operator, value, "or")
    }

    @JvmOverloads
    open fun havingBetween(column: String, values: List<Any?>, boolean: String = "and", not: Boolean = false): Builder {
        havings.add(hashMapOf(
            "type" to "Between",
            "column" to column,
            "values" to values,
            "boolean" to boolean,
            "not" to not
        ))
        addBinding(cleanBindings(values).subList(0, 2), "having")
        return this
    }

    @JvmOverloads
    open fun havingRaw(sql: String, bindings: List<Any?> = listOf(), boolean: String = "and"): Builder {
        havings.add(hashMapOf(
            "type" to "Raw",
            "sql" to sql,
            "boolean" to boolean
        ))
        addBinding(bindings, "having")
        return this
    }

    @JvmOverloads
    open fun orHavingRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
        return havingRaw(sql, bindings, "or")
    }

    @JvmOverloads
    open fun orderBy(column: Any, direction: String = "asc"): Builder {
        if (isQueryable(column)) {
            val sub = createSub(column)
            addBinding(sub.second, if (unions.isEmpty()) "order" else "unionOrder")
            return orderBy(Expression(sub.first), direction)
        }

        if (direction.lowercase() !in listOf("asc", "desc")) {
            throw InvalidArgumentException("Order direction must be \"asc\" or \"desc\".")
        }
        val order = hashMapOf(
            "column" to column,
            "direction" to direction
        )
        if (this.unions.isEmpty()) {
            orders.add(order)
        } else {
            unionOrders.add(order)
        }
        return this
    }

    open fun orderByDesc(column: String): Builder {
        return orderBy(column, "desc")
    }

    @JvmOverloads
    open fun latest(column: String = "created_at"): Builder {
        return orderBy(column, "desc")
    }

    @JvmOverloads
    open fun oldest(column: String = "created_at"): Builder {
        return orderBy(column, "asc")
    }

    open fun inRandomOrder(seed: String = ""): Builder {
        return orderByRaw(grammar.compileRandom(seed))
    }

    @JvmOverloads
    open fun orderByRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
        val type = "Raw"
        val order = hashMapOf("type" to type, "sql" to sql)
        if (unions.isEmpty()) {
            orders.add(order)
            addBinding(bindings, "order")
        } else {
            unionOrders.add(order)
            addBinding(bindings, "unionOrder")
        }
        return this
    }

    @JvmOverloads
    open fun reorder(column: String? = null, direction: String = "asc"): Builder {
        orders.clear()
        unionOrders.clear()
        bindings["order"] = mutableListOf()
        bindings["unionOrder"] = mutableListOf()
        if (column != null) {
            orderBy(column, direction)
        }
        return this
    }

    open fun limit(size: Int): Builder {
        if (size >= 0) {
            if (unions.isEmpty()) {
                this.limit = size
            } else {
                this.unionLimit = size
            }
        }
        return this
    }

    open fun take(size: Int) = limit(size)

    open fun offset(value: Int): Builder {
        val property = max(0, value)
        if (unions.isEmpty()) {
            this.offset = property
        } else {
            this.unionOffset = property
        }
        return this
    }

    open fun skip(value: Int) = offset(value)

    @JvmOverloads
    open fun forPage(page: Int, pageSize: Int = 15): Builder {
        return offset((page - 1) * pageSize).limit(pageSize)
    }

    @JvmOverloads
    open fun forPageBeforeId(pageSize: Int = 15, lastId: Long = 0L, column: String = "id"): Builder {
        orders = removeExistingOrdersFor(column)
        return where(column, "<", lastId).orderBy(column, "desc").limit(pageSize)
    }

    @JvmOverloads
    open fun forPageAfterId(pageSize: Int = 15, lastId: Long = 0L, column: String = "id"): Builder {
        orders = removeExistingOrdersFor(column)
        return where(column, ">", lastId).orderBy(column, "asc").limit(pageSize)
    }

    protected open fun removeExistingOrdersFor(column: String): MutableList<Map<String, Any?>> {
        return orders.filterNot {
            if (it.containsKey("column")) {
                it["column"] == column
            } else {
                false
            }
        }.toMutableList()
    }

    @JvmOverloads
    @Suppress("UNCHECKED_CAST")
    open fun union(queryOrClosure: Any, all: Boolean = false): Builder {
        var newQuery = queryOrClosure
        if (queryOrClosure is Function1<*, *>) {
            queryOrClosure as Function1<Builder, Unit>
            newQuery = newQuery()
            queryOrClosure(newQuery)
        }

        newQuery as Builder

        unions.add(hashMapOf(
            "query" to newQuery,
            "all" to all
        ))
        addBinding(newQuery.getFlattenBindings(), "union")
        return this
    }

    open fun unionAll(query: Builder) = union(query, true)

    open fun with(name: String, relation: Relation): Builder {
        withes[name] = hashMapOf( // 先用 map，以后万一加别的参数
            "relation" to relation
        )
        return this
    }

    open fun with(name: String, relation: () -> Relation): Builder {
        return with(name, relation())
    }

    open fun withAggregate(name: String, relation: Relation, function: String, column: String): Builder {
        val sub = buildAggregateSub(relation, function, column)
        selectSub(sub, name)
        return this
    }

    protected open fun buildAggregateSub(relation: Relation, function: String, column: String): Builder {
        return relationBuilder(relation).selectRaw(grammar.ifnull("$function(${grammar.wrap(column)})", 0))
    }

    protected open fun relationBuilder(relation: Relation): Builder {
        relation as Builder
        setUpEmptyQuery(relation)
        relation.withes.clear()
        return when(relation) {
            is HasMany -> hasManyBuilder(relation.copy())
            is BelongsTo -> belongsToBuilder(relation.copy())
            is BelongsToMany -> belongsToManyBuilder(relation.copy())
            else -> relation.copy()
        }
    }

    protected open fun aggregateColumn(relation: Relation, column: String): String {
        if (column == "*") return "*"
        if (column.contains(".")) return column
        return when(relation) {
            is HasMany -> "${relation.table}.$column"
            is BelongsTo -> "${relation.table}.$column"
            is BelongsToMany -> "${relation.table}.$column"
            else -> "*"
        }
    }

    protected open fun hasManyBuilder(relation: HasMany): Builder {
        val alias = getTableOrAlias()
        return relation.table(relation.table)
            .whereColumn("${relation.table}.${relation.foreignKey}", "=", "$alias.${relation.localKey}")
    }

    protected open fun belongsToBuilder(relation: BelongsTo): Builder {
        val alias = getTableOrAlias()
        return relation.table(relation.table)
            .whereColumn("${relation.table}.${relation.ownerKey}", "=", "$alias.${relation.foreignKey}")
    }

    protected open fun belongsToManyBuilder(relation: BelongsToMany): Builder {
        val alias = getTableOrAlias()
        return relation.table(relation.table)
            .join(relation.pivotTable, "${relation.pivotTable}.${relation.relatedPivotKey}", "=", "${relation.table}.${relation.relatedKey}")
            .whereColumn("${relation.pivotTable}.${relation.foreignPivotKey}", "=", "$alias.${relation.localKey}")
    }

    protected open fun getTableOrAlias(): String {
        val table = if (from is Expression) {
            (from as Expression).value as String
        } else {
            from as String
        }
        return table.split(Regex("\\s+as\\s+", RegexOption.IGNORE_CASE)).last()
    }

    @JvmOverloads
    open fun withCount(name: String, relation: Relation, column: String = "*"): Builder {
        return withAggregate(name, relation, "count", column)
    }

    open fun withSum(name: String, relation: Relation, column: String): Builder {
        return withAggregate(name, relation, "sum", column)
    }

    open fun withMin(name: String, relation: Relation, column: String): Builder {
        return withAggregate(name, relation, "min", column)
    }

    open fun withMax(name: String, relation: Relation, column: String): Builder {
        return withAggregate(name, relation, "max", column)
    }

    open fun withAvg(name: String, relation: Relation, column: String): Builder {
        return withAggregate(name, relation, "avg", column)
    }

    open fun getFlattenBindings(): List<Any?> {
        return flatten(bindings)
    }

    open fun getRawBindings(): MutableMap<String, MutableList<Any?>> {
        return bindings
    }

    open fun addBinding(value: Any?, type: String = "where"): Builder {
        if (type !in bindings.keys) {
            throw InvalidArgumentException("Invalid binding type: {$type}.")
        }
        if (value is List<*>) {
            bindings[type]!!.addAll(value)
        } else {
            bindings[type]!!.add(value)
        }
        return this
    }

    open fun cleanBindings(bindings: List<Any?>): List<Any?> {
        return bindings.filter { it !is Expression }
    }

    open fun mergeBinding(builder: Builder): Builder {
        bindings.map { binding ->
            binding.value.addAll(builder.bindings[binding.key]!!)
        }
        return this
    }

    protected open fun forNestedWhere(): Builder {
        return Builder().from(this.from)
    }

    @Suppress("UNCHECKED_CAST")
    open fun get(): List<Map<String, Any?>> {
        var result = runSelect<List<*>>() as List<Map<String, Any?>>
        if (withes.isNotEmpty()) {
            withes.forEach { (name, map) ->
                val relation = map["relation"] as Relation
                result = resolveRelation(result, name, relation)
            }
        }
        return result
    }

    open fun <T: Any> get(clazz: KClass<T>): List<T> {
        return get(clazz.java)
    }

    open fun <T : Any> get(klass: Class<T>): List<T> {
        val result = get()
        val jsonMapper = JsonBuilder.build()
        val json = jsonMapper.writeValueAsString(result)
        return jsonMapper.readValue(json, jsonMapper.typeFactory.constructCollectionType(List::class.java, klass))
    }

    protected open fun resolveRelation(result: List<Map<String, Any?>>, name: String, relation: Relation): List<Map<String, Any?>> {
        return when (relation) {
            is HasMany -> resolveHasMany(result, name, relation.copy())
            is BelongsTo -> resolveBelongsTo(result, name, relation.copy())
            is BelongsToMany -> resolveBelongsToMany(result, name, relation.copy())
            else -> result
        }
    }

    protected open fun resolveHasMany(
        result: List<Map<String, Any?>>,
        name: String,
        relation: HasMany
    ): List<Map<String, Any?>> {
        val keys = result.map { it[relation.localKey] }.distinct()
        setUpEmptyQuery(relation)
        relation.table(relation.table).whereIn(relation.foreignKey, keys)
        val data = relation.get()
        result.forEach {
            it as MutableMap
            // 通过字符串比较，防止类型不统一造成的不相等
            if (relation is HasOne) {
                it[name] = data.firstOrNull { datum -> datum[relation.foreignKey].toString() == it[relation.localKey].toString() }
            } else {
                it[name] = data.filter { datum -> datum[relation.foreignKey].toString() == it[relation.localKey].toString() }
            }
        }
        return result
    }

    protected open fun resolveBelongsTo(
        result: List<Map<String, Any?>>,
        name: String,
        relation: BelongsTo
    ): List<Map<String, Any?>> {
        val keys = result.map { it[relation.foreignKey] }.distinct()
        setUpEmptyQuery(relation)
        relation.table(relation.table).whereIn(relation.ownerKey, keys)
        val data = relation.get()
        result.forEach {
            it as MutableMap
            it[name] = data.firstOrNull { datum -> datum[relation.ownerKey].toString() == it[relation.foreignKey].toString() }
        }
        return result
    }

    @Suppress("UNCHECKED_CAST")
    protected open fun resolveBelongsToMany(
        result: List<Map<String, Any?>>,
        name: String,
        relation: BelongsToMany
    ): List<Map<String, Any?>> {
        val keys = result.map { it[relation.localKey] }.distinct()
        setUpEmptyQuery(relation)
        relation.table(relation.table)
            .join(relation.pivotTable, "${relation.pivotTable}.${relation.relatedPivotKey}", "=", "${relation.table}.${relation.relatedKey}")
        relation.wherePivotIn(relation.foreignPivotKey, keys)

        val data = relation
            .selectRaw("${relation.pivotTable}.${relation.foreignPivotKey} as _pivot_id, ${relation.table}.*")
            .get()
        //中间表数据
        val newQuery = newQuery()
        replayBelongsToMany(newQuery, relation)
        val pivots = newQuery.table(relation.pivotTable).get()
        result.forEach { res ->
            res as MutableMap
            res[name] = data.filter { datum -> datum["_pivot_id"].toString() == res[relation.localKey].toString() }
                .map {
                    it as MutableMap
                    it["pivot_table"] = pivots.firstOrNull { pivot ->
                        pivot[relation.foreignPivotKey].toString() == res[relation.localKey].toString() &&
                                pivot[relation.relatedPivotKey].toString() == it[relation.relatedKey].toString()
                    }
                    it
                }
        }
        // 删除 _pivot_id 列，不能在 前面删，否则导致结果异常
        result.forEach {
            (it[name] as List<MutableMap<String, Any?>>).forEach { row ->
                row.remove("_pivot_id")
            }
        }
        return result
    }

    protected open fun replayBelongsToMany(builder: Builder, relation: BelongsToMany) {
        relation.wherePivots.forEach {
            builder.where(it[0]!!, it[1] as String?, it[2], it[3] as String)
        }
        relation.wherePivotBetweens.forEach {
            builder.whereBetween(it[0] as String, it[1] as List<Any?>, it[2] as String, it[3] as Boolean)
        }
        relation.wherePivotIns.forEach {
            builder.whereIn(it[0] as String, it[1] as Any, it[2] as String, it[3] as Boolean)
        }
        relation.wherePivotNulls.forEach {
            builder.whereNull(it[0] as String, it[1] as String, it[2] as Boolean)
        }
        relation.orderBys.forEach {
            builder.orderBy(it[0] as String, it[1] as String)
        }
    }

    protected open fun setUpEmptyQuery(builder: Builder) {
        if (builder.jdbcTemplate == null) {
            builder.jdbcTemplate = jdbcTemplate
            builder.grammar = grammar
        }
    }

    @JvmOverloads
    open fun paginate(page: Int = 1, pageSize: Int = 15): Page {
        val total = getCountForPagination()
        val results = if (total > 0) {
            forPage(page, pageSize).get()
        } else {
            listOf()
        }
        return Page.new(page, pageSize, total, results)
    }

    open fun <T: Any> paginate(clazz: KClass<T>, page: Int = 1, pageSize: Int = 15) = paginate(clazz.java, page, pageSize)

    @JvmOverloads
    open fun <T: Any> paginate(klass: Class<T>, page: Int = 1, pageSize: Int = 15): Page{
        val total = getCountForPagination()
        val results = if (total > 0) {
                forPage(page, pageSize).get(klass)
        } else {
            listOf()
        }
        return Page.new(page, pageSize, total, results)
    }

    open fun <T : Any> paginateT(clazz: KClass<T>, page: Int, pageSize: Int): PageT<T> = paginateT(clazz.java, page, pageSize)

    open fun <T: Any> paginateT(klass: Class<T>, page: Int = 1, pageSize: Int = 15): PageT<T> {
        val total = getCountForPagination()
        val results = if (total > 0) {
            forPage(page, pageSize).get(klass)
        } else {
            listOf()
        }
        return PageT.new(page, pageSize, total, results)
    }

    protected open fun getCountForPagination(): Long {
        val result = runPaginationCountQuery()
        if (result.isEmpty()) return 0

        return result.first()["aggregate"] as Long
    }

    protected open fun runPaginationCountQuery(): List<Map<String, Any?>> {
        if (groups.isNotEmpty() || havings.isNotEmpty()) {
            val clone = cloneForPaginationCount()

            if (clone.columns.isEmpty() && joins.isNotEmpty()) {
                clone.select("$from.*")
            }
            return newQuery()
                .from(Expression("(${clone.toSql()}) as ${grammar.wrap("aggregate_table")}"))
                .mergeBinding(clone)
                .setAggregate("count", listOf("*"))
                .get()
        }
        return cloneWithout(if (unions.isEmpty()) listOf("columns", "orders", "limit", "offset", "withes") else listOf("orders", "limit", "offset", "withes"))
            .cloneWithoutBindings(if (unions.isEmpty()) listOf("select", "order") else listOf("order"))
            .setAggregate("count", listOf("*"))
            .get()
    }

    protected open fun cloneForPaginationCount(): Builder {
        return cloneWithout(listOf("orders", "limit", "offset", "withes"))
            .cloneWithoutBindings(listOf("orders"))
    }

    protected open fun printDebugInfo(sql: String, bindings: Any?) {
        log.debug("- sql: $sql")
        log.debug("- bindings: $bindings")
    }

    protected open fun <T : Any> runSelect(): List<Any> {
        val sql = toSql()
        val bindings = getFlattenBindings()
        printDebugInfo(sql, bindings)
        return jdbcTemplate!!.queryForList(sql, *bindings.toTypedArray())
    }

    open fun exists(): Boolean {
        val sql = grammar.compileExists(this)
        val bindings = getFlattenBindings()
        printDebugInfo(sql, bindings)
        return jdbcTemplate!!.queryForMap(sql, *bindings.toTypedArray())["exists"].toBool()
    }

    open fun notExists(): Boolean = !exists()
    open fun doesntExists(): Boolean = notExists()

    @JvmOverloads
    open fun count(column: String = "*"): Long {
        return aggregate("count", column) as Long
    }

    open fun min(column: String): Any? {
        return aggregate("min", column)
    }

    open fun max(column: String): Any? {
        return aggregate("max", column)
    }

    open fun sum(column: String): Any {
        return aggregate("sum", column) ?: 0
    }

    open fun avg(column: String): Any? {
        return aggregate("avg", column)
    }

    open fun average(column: String) = avg(column)

    open fun aggregate(function: String, vararg columns: String): Any? {
        val results = cloneWithout(if(unions.isNotEmpty()) listOf() else listOf("columns"))
            .cloneWithoutBindings(if (unions.isNotEmpty()) listOf() else listOf("select"))
            .setAggregate(function, columns.toList())
            .select(*columns.map { it }.toTypedArray())
            .get()
        if (results.isNotEmpty()) {
            return results.first()["aggregate"]
        }
        return null
    }

    open fun numericAggregate(function: String, vararg column: String): Number {
        val result = aggregate(function, *column) ?: return 0

        return result as Number
    }

    protected open fun setAggregate(function: String, columns: List<String>): Builder {
        aggregate = hashMapOf(
            "function" to function,
            "columns" to columns
        )
        if (groups.isEmpty()) {
            orders = mutableListOf()
            bindings["order"] = mutableListOf()
        }
        return this
    }

    open fun cloneWithoutBindings(excepts: List<String>): Builder {
        return this.copy().also { builder ->
            excepts.forEach { except ->
                builder.bindings[except] = mutableListOf()
            }
        }
    }

    open fun cloneWithout(properties: List<String>): Builder {
        return this.copy().also { builder ->
            properties.forEach { name ->
                when (name) {
                    "columns" -> builder.columns = mutableListOf()
                    "limit" -> builder.limit = null
                    "offset" -> builder.offset = null
                    "orders" -> builder.orders = mutableListOf()
                    "withes" -> builder.withes.clear()
                }
            }
        }
    }

    protected open fun onceWithColumns(fields: List<Any>, callback: () -> List<Any>): List<Any> {
        val original = this.columns
        if (empty(original)) {
            this.columns.addAll(fields)
        }
        val result = callback()
        this.columns = original
        return result
    }

    @JvmOverloads
    open fun firstWhere(column: String, operator: String, value: Any?, boolean: String = "and"): Map<String, Any?>? {
        return where(column, operator, value, boolean).first()
    }

    open fun first(): Map<String, Any?>? {
        return take(1).get().firstOrNull()
    }

    open fun <T: Any> first(clazz: KClass<T>) = first(clazz.java)

    open fun <T: Any> first(klass: Class<T>): T? {
        return take(1).get(klass).firstOrNull()
    }

    @JvmOverloads
    open fun ifTrue(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null): Builder {
        if (value) callback(this) else if (default != null) default(this)
        return this
    }
    @JvmOverloads
    open fun whenTrue(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifTrue(value, callback, default)
    @JvmOverloads
    open fun ifFalse(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifTrue(!value, callback, default)
    @JvmOverloads
    open fun whenFalse(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifFalse(value, callback, default)

    open fun tap(callback: (Builder) -> Unit): Builder {
        return whenTrue(true, callback)
    }

    open fun find(id: Any): Map<String, Any?>? {
        return where("id", "=", id).first()
    }

    open fun <T: Any> find(id: Any, clazz: KClass<T>) = find(id, clazz.java)

    open fun <T: Any> find(id: Any, klass: Class<T>): T? {
        return where("id", "=", id).first(klass)
    }

    open fun sole(): Map<String, Any?> {
        val records = take(2).get()
        return getSole(records)
    }

    open fun <T: Any> sole(clazz: KClass<T>) = sole(clazz.java)

    open fun <T: Any> sole(klass: Class<T>): T {
        val records = take(2).get(klass)
        return getSole(records)
    }

    protected open fun <T: Any> getSole(records: List<T>): T {
        if (records.isEmpty()) {
            throw RecordsNotFoundException()
        }
        if (records.size > 1) {
            throw MultipleRecordsFoundException(records.size)
        }
        return records.first()
    }

    open fun value(column: String): Any? {
        val result = first()
        return result?.get(column)
    }

    open fun soleValue(column: String): Any? {
        return sole()[column]
    }

    open fun chunk(count: Int, callback:(List<Map<String, Any?>>, Int) -> Boolean): Boolean {
        enforceOrderBy()
        var page = 1
        do {
            val results = forPage(page, count).get()
            val countResult = results.size
            if (countResult == 0) break
            if (!callback(results, page)) {
                return false
            }
            page ++
        } while (count == countResult)
        return true
    }

    open fun <T: Any> chunk(clazz: KClass<T>, count: Int, callback: (List<T>, Int) -> Boolean): Boolean {
        return chunk(clazz.java, count, callback)
    }

    @Suppress("unchecked_cast")
    open fun <T: Any> chunk(klass: Class<T>, count: Int, callback: (List<T>, Int) -> Boolean): Boolean {
        enforceOrderBy()
        var page = 1
        do {
            val results = forPage(page, count).get(klass)
            val countResult = results.size
            if (countResult == 0) break
            if (!callback(results, page)) {
                return false
            }
            page ++
        } while (count == countResult)
        return true
    }

    open fun each(callback: (Map<String, Any?>, Int) -> Boolean, count: Int = 1000): Boolean {
        return chunk(count) { rows, page ->
            rows.forEachIndexed { index, row ->
                if (!callback(row, (page - 1) * count + index)) {
                    return@chunk false
                }
            }
            true
        }
    }

    open fun <T: Any> each(clazz: KClass<T>, callback: (T, Int) -> Boolean, count: Int = 1000): Boolean {
        return each(clazz.java, callback, count)
    }

    open fun <T: Any> each(klass: Class<T>, callback: (T, Int) -> Boolean, count: Int = 1000): Boolean {
        return chunk(klass, count) { rows, page ->
            rows.forEachIndexed { index, row ->
                if (!callback(row, (page - 1) * count + index)) {
                    return@chunk false
                }
            }
            true
        }
    }

    open fun chunkById(count: Int, callback: (List<Map<String, Any?>>, Int) -> Boolean, column: String = "id"): Boolean {
        var lastId: Long? = 0
        var page = 1
        do {
            val clone = this.copy()
            val results = clone.forPageAfterId(count, lastId!!, column).get()
            val countResults = results.size
            if (countResults == 0) break
            if (!callback(results, page)) {
                return false
            }
            lastId = results.last()[column]?.toString()?.toLong()
            if (lastId == null) {
                throw RuntimeException("The chunkById operation was aborted because the [$column] column is not present in the query result.")
            }
            page ++
        } while (countResults == count)
        return true
    }

    open fun eachById(callback: (Map<String, Any?>, Int) -> Boolean, count: Int = 1000, column: String = "id"): Boolean {
        return chunkById(count, { rows, page ->
            rows.forEachIndexed { index, row ->
                if (!callback(row, (page - 1) * count + index)) {
                    return@chunkById false
                }
            }
            true
        }, column)
    }

    protected open fun enforceOrderBy() {
        if (orders.isEmpty() && unionOrders.isEmpty()) {
            throw RuntimeException("You must specify an orderBy clause when using this function.")
        }
    }

    @Suppress("unchecked_cast")
    open fun pluck(column: Any): List<Any?> {
        val queryResult = onceWithColumns(listOf(column)) {
            this.get()
        } as List<Map<String, Any?>>
        if (empty(queryResult)) {
            return emptyList<Any?>()
        }
        val columnName = stripTableForPluck(column)
        return queryResult.map { it[columnName] }
    }

    @JvmOverloads
    open fun implode(column: Any, glue: String = ""): String {
        return pluck(column).joinToString(glue)
    }

    @Suppress("unchecked_cast")
    open fun pluck(column: Any, key: Any): Map<String, Any?> {
        val queryResult = onceWithColumns(listOf(column, key)) {
            get()
        } as List<Map<String, Any?>>
        if (empty(queryResult)) {
            return emptyMap()
        }
        val columnName = stripTableForPluck(column)
        val keyName = stripTableForPluck(key)
        return queryResult.fold(linkedMapOf()) { acc, map ->
            acc[map[keyName].toString()] = map[columnName]
            acc
        }
    }

    protected open fun stripTableForPluck(column: Any?): String? {
        if (column == null) return null
        val columnName = if (column is Expression) {
            column.value as String
        } else {
            column as String
        }
        val separator = if (columnName.contains(" as ", true)) "\\s+as\\s+" else "\\."
        return columnName.split(Regex(separator, RegexOption.IGNORE_CASE)).last()
    }

    protected open fun isQueryable(value: Any?): Boolean {
        return value is Builder || value is Function1<*, *>
    }

    open fun toSql(): String {
        return grammar.compileSelect(this)
    }

    open fun dump(): Builder {
        println("sql:" + this.toSql())
        println("bindings" + this.getFlattenBindings().toString())
        return this
    }

    @JvmOverloads
    @Suppress("unchecked_cast")
    open fun insertGetId(data: Any, sequence: String = "id"): Long {
        val values = if (data is Map<*, *>) data as Map<String, Any?> else classToMapForBuilder(data)
        val sql = grammar.compileInsertGetId(this, values, sequence)
        val parameters = values.filter { it.value !is Expression }.keys.sorted().map { values[it] }
        printDebugInfo(sql, parameters)
        val keyHolder = GeneratedKeyHolder()

        jdbcTemplate!!.update({ conn ->
            val ps = conn.prepareStatement(sql, arrayOf(sequence))
            parameters.forEachIndexed { index, param ->
                ps.setObject(index + 1, param)
            }
            ps
        }, keyHolder)
        return keyHolder.key?.toLong() ?: 0
    }

    @JvmOverloads
    open fun insert(data: Any, batch: Int = 0): Int {
        val values = transformDataToList(data)
        if (batch != 0) {
            var effected = 0
            values.chunked(batch).forEach {
                effected += insert(it, 0)
            }
            return effected
        }
        val sql = grammar.compileInsert(this, values)
        return insert(sql, values)
    }

    @Suppress("unchecked_cast")
    protected open fun transformDataToList(data: Any): List<Map<String, Any?>> {
        return when (data) {
            is Map<*, *> -> listOf(data as Map<String, Any?>)
            is List<*> -> run {
                if (data.size == 0) {
                    throw InvalidArgumentException("list must at least one element")
                }
                if (data[0] is Map<*, *>) {
                    data as List<Map<String, Any?>>
                } else {
                    data.map { classToMapForBuilder(it!!) }
                }
            }
            else -> listOf(classToMapForBuilder(data))
        }
    }

    protected open fun insert(sql: String, values: List<Map<String, Any?>>): Int {
        var parameters = mutableListOf<Any?>()
        val columns = values.first().keys.sorted()
        values.forEach { value ->
            columns.forEach {
                parameters.add(value[it])
            }
        }
        parameters = cleanBindings(parameters) as MutableList<Any?>
        printDebugInfo(sql, parameters)
        return jdbcTemplate!!.update(sql, *parameters.toTypedArray())
    }

    @JvmOverloads
    open fun insertOrIgnore(data: Any, batch: Int = 0): Int {
        val values = transformDataToList(data)
        if (batch != 0) {
            var effected = 0
            values.chunked(batch).forEach {
                effected += insertOrIgnore(it, 0)
            }
            return effected
        }
        val sql = grammar.compileInsertOrIgnore(this, values)
        return insert(sql, values)
    }

    open fun insertUsing(columns: List<String>, query: Any): Int {
        val sub = createSub(query)
        val sql = grammar.compileInsertUsing(this, columns, sub.first)
        val bindings = cleanBindings(sub.second)
        printDebugInfo(sql, bindings)
        return jdbcTemplate!!.update(sql, *bindings.toTypedArray())
    }

    /**
     * 更新记录，可以结合 where 条件更新多条数据, 没有任何 where 条件则会更新整个表!
     * 特意不支持 [com.tianyisoft.database.Table] 的实例, 因为可能更新多条，和 Table 代表一条数据的意义冲突
     * */
    @Suppress("unchecked_cast")
    open fun update(values: Map<String, Any?>): Int {
        val sql = grammar.compileUpdate(this, values)
        val parameters = cleanBindings(grammar.prepareBindingsForUpdate(bindings, values))
        printDebugInfo(sql, parameters)
        return jdbcTemplate!!.update(sql, *parameters.toTypedArray())
    }

    open fun updateOrInsert(attributes: Map<String, Any?>, values: Map<String, Any?>): Int {
        if (!where(attributes).exists()) {
            return insert(attributes + values)
        }
        if (values.isEmpty()) {
            return 0
        }
        return limit(1).update(values)
    }

    @Suppress("unchecked_cast")
    open fun upsert(data: Any, update: Map<String, Any?>, uniqueBy: List<String>): Int {
        val values = when (data) {
            is Map<*, *> -> {
                if (data.isEmpty()) return 0
                listOf(data as Map<String, Any?>)
            }
            is List<*> -> {
                if (data.isEmpty()) return 0
                data as List<Map<String, Any?>>
            }
            else -> {
                throw InvalidArgumentException("data must be Map<String, Any?> or List<Map<String, Any?>>")
            }
        }.toMutableList()
        if (update.isEmpty()) return insert(values)
        val sortedUpdate = update.toSortedMap()
        val sql = grammar.compileUpsert(this, values, sortedUpdate, uniqueBy)
        values.add(sortedUpdate)
        return insert(sql, values)
    }

    /**
     *  更新记录，[data]可以是 Map<String, Any> 也可以是 [com.tianyisoft.database.Table] 类的实例
     *
     *  @return 影响的条数
     */
    @Suppress("unchecked_cast")
    open fun updateById(id: Any, data: Any): Int {
        val values = if (data is Map<*, *>) data as Map<String, Any?> else classToMapForBuilder(data)
        where("$from.id", "=", id)
        return update(values)
    }

    @JvmOverloads
    open fun increment(column: String, amount: Number = 1, extra: Map<String, Any?> = mutableMapOf()): Int {
        extra as MutableMap
        val wrapped = grammar.wrap(column)
        extra[column] = Expression("$wrapped + $amount")
        return update(extra)
    }

    @JvmOverloads
    open fun decrement(column: String, amount: Number = -1, extra: Map<String, Any?> = mutableMapOf()): Int {
        extra as MutableMap
        val wrapped = grammar.wrap(column)
        extra[column] = Expression("$wrapped - $amount")
        return update(extra)
    }

    @JvmOverloads
    open fun delete(id: Any? = null): Int {
        if (id != null) {
            where("$from.id", "=", id)
        }
        if (softDelete) {
            if (deletedDataType == DeletedDataType.INTEGER) {
                return update(mapOf(deletedColumn to 1))
            }
            return update(mapOf(deletedColumn to Date()))
        }

        val sql = grammar.compileDelete(this)
        val parameters = cleanBindings(grammar.prepareBindingsForDelete(bindings))
        printDebugInfo(sql, parameters)
        return jdbcTemplate!!.update(sql, *parameters.toTypedArray())
    }

    open fun truncate() {
        grammar.compileTruncate(this).forEach { (sql, binding) ->
            printDebugInfo(sql, binding)
            jdbcTemplate!!.update(sql, *binding.toTypedArray())
        }
    }
    // JdbcTemplate 原本支持的方法
    open fun queryForList(sql: String, vararg args: Any?): List<Map<String, Any?>> {
        printDebugInfo(sql, args.toList())
        return jdbcTemplate!!.queryForList(sql, *args)
    }

    open fun <T : Any> queryForObject(sql: String, type: Class<T>, vararg args: Any?): T {
        printDebugInfo(sql, args.toList())
        return jdbcTemplate!!.queryForObject(sql, type, *args)
    }

    open fun queryForMap(sql: String, vararg args: Any?): MutableMap<String, Any?> {
        printDebugInfo(sql, args.toList())
        return jdbcTemplate!!.queryForMap(sql, *args)
    }

    public override fun clone(): Any {
        val builder = Builder()
        copyAttributes(builder)
        return builder
    }

    protected open fun copyAttributes(builder: Builder) {
        builder.jdbcTemplate = jdbcTemplate
        builder.grammar = grammar
        builder.from = from
        builder.limit = limit
        builder.unionLimit = unionLimit
        builder.offset = offset
        builder.unionOffset = unionOffset
        builder.wheres.addAll(wheres)
        builder.unions.addAll(unions)
        builder.joins.addAll(joins)
        builder.groups.addAll(groups)
        builder.havings.addAll(havings)
        builder.orders.addAll(orders)
        builder.unionOrders.addAll(unionOrders)
        aggregate.forEach { (t, u) ->
            builder.aggregate[t] = u
        }
        builder.distinct = distinct
        builder.columns = columns.map { it } as MutableList
        bindings.forEach { (t, u) ->
            builder.bindings[t] = u.map { it } as MutableList
        }
        withes.forEach { (name, relation) ->
            builder.withes[name] = relation
        }
    }

    open fun copy(): Builder {
        return clone() as Builder
    }
}
