package com.tianyisoft.database

import com.tianyisoft.database.exceptions.InvalidArgumentException
import com.tianyisoft.database.grammar.Grammar
import com.tianyisoft.database.grammar.MysqlGrammar
import com.tianyisoft.database.processor.MySqlProcessor
import com.tianyisoft.database.processor.Processor
import com.tianyisoft.database.util.*
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.support.GeneratedKeyHolder
import kotlin.math.max
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

    private val operators = listOf(
        "=", "<", ">", "<=", ">=", "<>", "!=", "<=>",
        "like", "like binary", "not like", "ilike",
        "&", "|", "^", "<<", ">>",
        "rlike", "not rlike", "regexp", "not regexp",
        "~", "~*", "!~", "!~*", "similar to",
        "not similar to", "not ilike", "~~*", "!~~*",
    )

    var jdbcTemplate:JdbcTemplate? = null
    var grammar: Grammar = MysqlGrammar()
    var processor: Processor = MySqlProcessor()

    init {
        listOf("select", "from", "join", "where", "groupBy", "having", "order", "union", "unionOrder").forEach {
            bindings[it] = mutableListOf()
        }
    }

    fun select(vararg fields: Any): Builder {
        columns = if (fields.isEmpty()) mutableListOf("*") else fields.toMutableList()
        bindings["select"] = mutableListOf()
        return this
    }

    fun selectRaw(fields: String, bindings: List<Any?> = listOf()): Builder {
        columns.add(Expression(fields))
        addBinding(bindings, "select")
        return this
    }

    fun selectSub(queryOrClosure: Any, alias: String): Builder {
        val sub = createSub(queryOrClosure)
        return selectRaw("(${sub.first}) as ${grammar.wrapTable(alias)}", sub.second)
    }

    fun distinct(boolean: Boolean = true): Builder {
        distinct = boolean
        return this
    }

    fun from(table: Any?, alias: String? = null): Builder {
        if (table is Builder || table is Function1<*, *>) {
            if (alias == null) {
                throw InvalidArgumentException("alias must not be null")
            }
            return fromSub(table, alias)
        }
        from = if (alias == null) table else "$table as $alias"
        return this
    }

    fun table(table: String, alias: String? = null): Builder {
        return from(table, alias)
    }

    fun fromRaw(expression: String, bindings: List<Any?> = listOf()): Builder {
        from = Expression(expression)
        addBinding(bindings, "from")
        return this
    }
    fun fromSub(queryOrClosure: Any, alias: String): Builder {
        val sub = createSub(queryOrClosure)
        return fromRaw("(${sub.first}) as ${grammar.wrapTable(alias)}", sub.second)
    }

    @Suppress("UNCHECKED_CAST")
    protected fun createSub(queryOrClosure: Any): Pair<String, List<Any?>> {
        var query = queryOrClosure
        if (queryOrClosure is Function1<*, *>) {
            queryOrClosure as Function1<Builder, Unit>
            query = forSubQuery()
            queryOrClosure(query)
        }
        return parseSub(query)
    }

    protected fun parseSub(query: Any): Pair<String, List<Any?>> {
        return when (query) {
            is Builder -> Pair(query.toSql(), query.getFlattenBindings())
            is String -> Pair(query, listOf())
            else -> throw InvalidArgumentException("A subquery must be a query builder instance, a Closure, or a string.")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun join(table: Any, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner", where: Boolean = false): Builder {
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

    fun joinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner") =
        join(table, firstOrClosure, operator, second, type, true)

    fun joinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null, type: String = "inner", where: Boolean = false): Builder {
        val sub = createSub(queryOrClosure)
        val expression = Expression("(${sub.first}) as ${grammar.wrapTable(alias)}")
        addBinding(sub.second, "join")
        return join(expression, firstOrClosure, operator, second, type, where)
    }

    fun leftJoin(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return join(table, firstOrClosure, operator, second, "left")
    }

    fun leftJoinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return joinWhere(table, firstOrClosure, operator, second, "left")
    }

    fun leftJoinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null) =
        joinSub(queryOrClosure, alias, firstOrClosure, operator, second, "left")

    fun rightJoin(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return join(table, firstOrClosure, operator, second, "right")
    }

    fun rightJoinWhere(table: String, firstOrClosure: Any, operator: String? = null, second: String? = null): Builder {
        return joinWhere(table, firstOrClosure, operator, second, "right")
    }

    fun rightJoinSub(queryOrClosure: Any, alias: String, firstOrClosure: Any, operator: String? = null, second: String? = null) =
        joinSub(queryOrClosure, alias, firstOrClosure, operator, second, "right")


    fun crossJoin(table: String, firstOrClosure: Any? = null, operator: String? = null, second: String? = null): Builder {
        if (firstOrClosure != null) {
            return join(table, firstOrClosure, operator!!, second!!, "cross")
        }
        joins.add(newJoinClause(this, "cross", table))
        return this
    }
    protected fun newJoinClause(query: Builder, type: String, table: Any): JoinClause {
        return JoinClause(query, type, table)
    }

    @Suppress("UNCHECKED_CAST")
    fun where(column: Any, operator: String? = null, value: Any? = null, boolean: String = "and"): Builder {
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

    fun orWhere(column: Any, operator: String? = null, value: Any? = null) = where(column, operator, value, "or")

    @Suppress("UNCHECKED_CAST")
    fun whereColumn(first: Any, operator: String? = null, second: String? = null, boolean: String = "and"): Builder {
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

    fun orWhereColumn(first: String, operator: String, second: String) = whereColumn(first, operator, second, "or")

    fun whereNull(columns: Any, boolean: String = "and", not: Boolean = false): Builder {
        if (columns !is String && columns !is List<*> && columns !is Array<*> && columns !is Set<*>) {
            throw InvalidArgumentException("columns must be String or List<String> or Array<String> or Set<String")
        }
        wrapListString(columns).forEach {
            wheres.add(hashMapOf(
                "type" to if (not) "NotNull" else "Null",
                "column" to it,
                "boolean" to boolean
            ))
        }
        return this
    }

    fun whereNotNull(column: String, boolean: String = "and") = whereNull(column, boolean, true)
    fun orWhereNull(column: String) = whereNull(column, "or")
    fun orWhereNotNull(column: String) = whereNotNull(column, "or")

    fun whereRaw(sql: String, bindings: List<Any?>, boolean: String = "and"): Builder {
        wheres.add(hashMapOf(
            "type" to "Raw",
            "sql" to sql,
            "boolean" to boolean
        ))
        addBinding(bindings)
        return this
    }

    fun orWhereRaw(sql: String, bindings: List<Any?>) = whereRaw(sql, bindings, "or")

    fun whereIn(column: String, values: Any, boolean: String = "and", not: Boolean = false): Builder {
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

    fun whereNotIn(column: String, values: List<Any?>, boolean: String = "and") = whereIn(column, values, boolean, true)
    fun orWhereIn(column: String, values: List<Any?>) = whereIn(column, values, "or")
    fun orWhereNotIn(column: String, values: List<Any?>) = whereNotIn(column, values, "or")

    fun whereBetween(column: String, values: List<Any?>, boolean: String = "and", not: Boolean = false): Builder {
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

    fun whereNotBetween(column: String, values: List<Any?>, boolean: String = "and") = whereBetween(column, values, boolean, true)
    fun orWhereBetween(column: String, values: List<Any?>) = whereBetween(column, values, "or")
    fun orWhereNotBetween(column: String, values: List<Any?>) = whereNotBetween(column, values, "or")

    fun whereBetweenColumns(column: String, values: List<String>, boolean: String = "and", not: Boolean = false): Builder {
        wheres.add(hashMapOf(
            "type" to "BetweenColumns",
            "column" to column,
            "values" to values,
            "boolean" to boolean,
            "not" to not
        ))
        return this
    }

    fun whereDate(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Date", column, operator, value, boolean)
    }
    fun orWhereDate(column: String, operator: String, value: Any?) = whereDate(column, operator, value, "or")

    fun whereTime(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Time", column, operator, value, boolean)
    }

    fun orWhereTime(column: String, operator: String, value: Any?) = whereTime(column, operator, value, "or")

    fun whereDay(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        var value2 = value
        if (value !is Expression) {
            value2 = value?.toString()?.padStart(2, '0')
        }
        return addDateBasedWhere("Day", column, operator, value2, boolean)
    }

    fun orWhereDay(column: String, operator: String, value: Any?) = whereDay(column, operator, value, "or")

    fun whereMonth(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        var value2 = value
        if (value !is Expression) {
            value2 = value?.toString()?.padStart(2, '0')
        }
        return addDateBasedWhere("Month", column, operator, value2, boolean)
    }

    fun orWhereMonth(column: String, operator: String, value: Any?) = whereMonth(column, operator, value, "or")

    fun whereYear(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
        return addDateBasedWhere("Year", column, operator, value, boolean)
    }

    fun orWhereYear(column: String, operator: String, value: Any?) = whereYear(column, operator, value, "or")

    protected fun addDateBasedWhere(type: String, column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
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

    protected fun whereSub(column: String, operator: String?, sub: (Builder) -> Unit, boolean: String = "and"): Builder {
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

    fun whereExists(callback: (Builder) -> Unit, boolean: String = "and", not: Boolean = false): Builder {
        val query = forSubQuery()
        callback(query)
        return addWhereExistsQuery(query, boolean, not)
    }

    fun whereNotExists(callback: (Builder) -> Unit, boolean: String = "and") = whereExists(callback, boolean, true)
    fun orWhereExists(callback: (Builder) -> Unit, not: Boolean = false) = whereExists(callback, "or", not)
    fun orWhereNotExists(callback: (Builder) -> Unit) = whereNotExists(callback, "or")

    fun addWhereExistsQuery(query: Builder, boolean: String = "and", not: Boolean = false): Builder {
        wheres.add(hashMapOf(
            "type" to if (not) "NotExists" else "Exists",
            "query" to query,
            "boolean" to boolean
        ))
        addBinding(query.getFlattenBindings())
        return this
    }

    protected open fun forSubQuery(): Builder {
        return newQuery()
    }

    open fun newQuery(): Builder {
        val builder =  Builder()
        builder.jdbcTemplate = jdbcTemplate
        builder.grammar = grammar
        builder.processor = processor
        return builder
    }

    protected fun addMapOfWheres(column: Map<String, Any?>, boolean: String, method: String = "where"): Builder {
        return whereNested({
            column.forEach { (c, v) -> it::class.memberFunctions.first { f -> f.name == method && f.parameters.size == 5 }.call(it, c, "=", v, boolean) }
        }, boolean)
    }

    protected fun addListOfWheres(column: List<List<Any?>>, boolean: String, method: String = "where"): Builder {
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

    fun whereNested(callback: (Builder) -> Unit, boolean: String = "and"): Builder {
        val query = forNestedWhere()
        callback(query)
        return addNestedWhereQuery(query, boolean)
    }

    protected fun addNestedWhereQuery(query: Builder, boolean: String): Builder {
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

    fun groupBy(vararg groups: String): Builder {
        this.groups.addAll(groups)
        return this
    }

    fun groupByRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
        this.groups.add(Expression(sql))
        addBinding(bindings, "groupBy")
        return this
    }

    fun having(column: String, operator: String, value: Any?, boolean: String = "and"): Builder {
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

    fun orHaving(column: String, operator: String, value: Any?): Builder {
        return having(column, operator, value, "or")
    }

    fun havingBetween(column: String, values: List<Any?>, boolean: String = "and", not: Boolean = false): Builder {
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

    fun havingRaw(sql: String, bindings: List<Any?> = listOf(), boolean: String = "and"): Builder {
        havings.add(hashMapOf(
            "type" to "Raw",
            "sql" to sql,
            "boolean" to boolean
        ))
        addBinding(bindings, "having")
        return this
    }

    fun orHavingRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
        return havingRaw(sql, bindings, "or")
    }

    fun orderBy(column: Any, direction: String = "asc"): Builder {
        if (isQueryable(column)) {
            val sub = createSub(column)
            addBinding(sub.second, if (unions.isEmpty()) "order" else "unionOrder")
            return orderBy(Expression(sub.first), direction)
        }

        val lowerDirection = direction.lowercase()
        if (lowerDirection !in listOf("asc", "desc")) {
            throw InvalidArgumentException("Order direction must be \"asc\" or \"desc\".")
        }
        val order = hashMapOf(
            "column" to column,
            "direction" to lowerDirection
        )
        if (this.unions.isEmpty()) {
            orders.add(order)
        } else {
            unionOrders.add(order)
        }
        return this
    }

    fun orderByDesc(column: String): Builder {
        return orderBy(column, "desc")
    }

    fun latest(column: String = "created_at"): Builder {
        return orderBy(column, "desc")
    }

    fun oldest(column: String = "created_at"): Builder {
        return orderBy(column, "asc")
    }

    fun inRandomOrder(seed: String = ""): Builder {
        return orderByRaw(grammar.compileRandom(seed))
    }

    fun orderByRaw(sql: String, bindings: List<Any?> = listOf()): Builder {
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

    fun reorder(column: String? = null, direction: String = "asc"): Builder {
        orders.clear()
        unionOrders.clear()
        bindings["order"] = mutableListOf()
        bindings["unionOrder"] = mutableListOf()
        if (column != null) {
            orderBy(column, direction)
        }
        return this
    }

    fun limit(size: Int): Builder {
        if (size >= 0) {
            if (unions.isEmpty()) {
                this.limit = size
            } else {
                this.unionLimit = size
            }
        }
        return this
    }

    fun take(size: Int) = limit(size)

    fun offset(value: Int): Builder {
        val property = max(0, value)
        if (unions.isEmpty()) {
            this.offset = property
        } else {
            this.unionOffset = property
        }
        return this
    }

    fun skip(value: Int) = offset(value)

    fun forPage(page: Int, pageSize: Int = 15): Builder {
        return offset((page - 1) * pageSize).limit(pageSize)
    }

    fun forPageBeforeId(pageSize: Int = 15, lastId: Int = 0, column: String = "id"): Builder {
        orders = removeExistingOrdersFor(column)
        return where(column, "<", lastId).orderBy(column, "desc").limit(pageSize)
    }

    fun forPageAfterId(pageSize: Int = 15, lastId: Int = 0, column: String = "id"): Builder {
        orders = removeExistingOrdersFor(column)
        return where(column, ">", lastId).orderBy(column, "asc").limit(pageSize)
    }

    protected fun removeExistingOrdersFor(column: String): MutableList<Map<String, Any?>> {
        return orders.filterNot {
            if (it.containsKey("column")) {
                it["column"] == column
            } else {
                false
            }
        }.toMutableList()
    }

    @Suppress("UNCHECKED_CAST")
    fun union(queryOrClosure: Any, all: Boolean = false): Builder {
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

    fun unionAll(query: Builder) = union(query, true)

    fun getFlattenBindings(): List<Any?> {
        return flatten(bindings)
    }

    fun getRawBindings(): MutableMap<String, MutableList<Any?>> {
        return bindings
    }

    fun addBinding(value: Any?, type: String = "where"): Builder {
        if (type !in bindings.keys) {
            throw InvalidArgumentException("Invalid binding type: {$type}.");
        }
        if (value is List<*>) {
            bindings[type]!!.addAll(value)
        } else {
            bindings[type]!!.add(value)
        }
        return this
    }

    fun cleanBindings(bindings: List<Any?>): List<Any?> {
        return bindings.filter { it !is Expression }
    }

    fun mergeBinding(builder: Builder): Builder {
        bindings.map { binding ->
            binding.value.addAll(builder.bindings[binding.key]!!)
        }
        return this
    }

    protected fun forNestedWhere(): Builder {
        return Builder().from(this.from)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getObject(rowMapper: RowMapper<T>, vararg fields: Any): List<T> {
        val fieldList = if (fields.isEmpty()) listOf("*") else fields.toList()
        return onceWithColumns(fieldList) {
            this.processor.processSelect(this, runSelect<T>(rowMapper))
        } as List<T>
    }

    @Suppress("UNCHECKED_CAST")
    fun get(vararg fields: Any): List<Map<String, Any?>> {
        val fieldList = if (fields.isEmpty()) listOf("*") else fields.toList()
        return onceWithColumns(fieldList) {
            this.processor.processSelect(this, runSelect<List<*>>())
        } as List<Map<String, Any?>>
    }

    fun paginate(page: Int = 1, pageSize: Int = 15): Page {
        val total = getCountForPagination()
        val results = if (total > 0) {
            forPage(page, pageSize).get()
        } else {
            listOf()
        }
        return Page.new(page, pageSize, total, results)
    }

    fun <T : Any> paginateObject(rowMapper: RowMapper<T>, page: Int = 1, pageSize: Int = 15): Page {
        val total = getCountForPagination()
        val results = if (total > 0) {
            forPage(page, pageSize).getObject(rowMapper)
        } else {
            listOf<T>()
        }
        return Page.new(page, pageSize, total, results)
    }

    protected fun getCountForPagination(): Long {
        val result = runPaginationCountQuery()
        if (result.isEmpty()) return 0

        return result.first()["aggregate"] as Long
    }

    protected fun runPaginationCountQuery(): List<Map<String, Any?>> {
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
        return cloneWithout(if (unions.isEmpty()) listOf("columns", "orders", "limit", "offset") else listOf("orders", "limit", "offset"))
            .cloneWithoutBindings(if (unions.isEmpty()) listOf("select", "order") else listOf("order"))
            .setAggregate("count", listOf("*"))
            .get()
    }

    protected fun cloneForPaginationCount(): Builder {
        return cloneWithout(listOf("orders", "limit", "offset"))
            .cloneWithoutBindings(listOf("orders"))
    }

    private fun printDebugInfo(sql: String, bindings: Any?) {
        log.debug("- sql: $sql")
        log.debug("- bindings: $bindings")
    }

    protected fun <T : Any> runSelect(rowMapper: RowMapper<T>? = null): List<Any> {
        val sql = toSql()
        val bindings = getFlattenBindings()
        printDebugInfo(sql, bindings)
        if (rowMapper == null) {
            return jdbcTemplate!!.queryForList(sql, *bindings.toTypedArray())
        }
        return jdbcTemplate!!.query(sql, rowMapper, *bindings.toTypedArray())
    }

    fun exists(): Boolean {
        val sql = grammar.compileExists(this)
        val bindings = getFlattenBindings()
        printDebugInfo(sql, bindings)
        return jdbcTemplate!!.queryForMap(sql, *bindings.toTypedArray())["exists"].toBool()
    }

    fun notExists(): Boolean = !exists()
    fun doesntExists(): Boolean = notExists()

    fun count(column: String = "*"): Long {
        return aggregate("count", column) as Long
    }

    fun min(column: String): Any? {
        return aggregate("min", column)
    }

    fun max(column: String): Any? {
        return aggregate("max", column)
    }

    fun sum(column: String): Any {
        return aggregate("sum", column) ?: 0
    }

    fun avg(column: String): Any? {
        return aggregate("avg", column)
    }

    fun average(column: String) = avg(column)

    fun aggregate(function: String, vararg columns: String): Any? {
        val results = cloneWithout(if(unions.isNotEmpty()) listOf() else listOf("columns"))
            .cloneWithoutBindings(if (unions.isNotEmpty()) listOf() else listOf("select"))
            .setAggregate(function, columns.toList())
            .get(*columns.map { it }.toTypedArray())
        if (results.isNotEmpty()) {
            return results.first()["aggregate"]
        }
        return null
    }

    fun numericAggregate(function: String, vararg column: String): Number {
        val result = aggregate(function, *column) ?: return 0

        return result as Number
    }

    protected fun setAggregate(function: String, columns: List<String>): Builder {
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

    fun cloneWithoutBindings(excepts: List<String>): Builder {
        return this.copy().also { builder ->
            excepts.forEach { except ->
                builder.bindings[except] = mutableListOf()
            }
        }
    }

    fun cloneWithout(properties: List<String>): Builder {
        return this.copy().also { builder ->
            properties.forEach { name ->
                when (name) {
                    "columns" -> builder.columns = mutableListOf()
                    "limit" -> builder.limit = null
                    "offset" -> builder.offset = null
                    "orders" -> builder.orders = mutableListOf()
                }
            }
        }
    }

    protected fun onceWithColumns(fields: List<Any>, callback: () -> List<Any>): List<Any> {
        val original = this.columns
        if (empty(original)) {
            this.columns.addAll(fields)
        }
        val result = callback()
        this.columns = original
        return result
    }

    fun firstWhere(column: String, operator: String, value: Any?, boolean: String = "and"): Map<String, Any?>? {
        return where(column, operator, value, boolean).first()
    }

    fun first(vararg fields: String): Map<String, Any?>? {
        return take(1)
            .get(*fields.map { it }.toTypedArray())
            .firstOrNull()
    }

    fun <T: Any> firstObject(rowMapper: RowMapper<T>, vararg fields: String): T? {
        return take(1)
            .getObject(rowMapper, *fields.map { it }.toTypedArray())
            .firstOrNull()
    }

    fun ifTrue(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null): Builder {
        if (value) callback(this) else if (default != null) default(this)
        return this
    }
    fun whenTrue(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifTrue(value, callback, default)
    fun ifFalse(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifTrue(!value, callback, default)
    fun whenFalse(value: Boolean, callback: (Builder) -> Unit, default: ((Builder) -> Unit)? = null) = ifFalse(value, callback, default)

    fun tap(callback: (Builder) -> Unit): Builder {
        return whenTrue(true, callback)
    }

    fun find(id: Any, vararg fields: String): Map<String, Any?>? {
        return where("id", "=", id).first(*fields.map { it }.toTypedArray())
    }

    fun <T: Any> findObject(id: Any, rowMapper: RowMapper<T>, vararg fields: String): T? {
        return where("id", "=", id).firstObject(rowMapper, *fields.map { it }.toTypedArray())
    }

    fun value(column: String): Any? {
        val result = first(column)
        return result?.values?.first()
    }

    fun chunk(count: Int, callback:(List<Map<String, Any?>>) -> Boolean): Boolean {
        enforceOrderBy()
        var page = 1
        do {
            val results = forPage(page, count).get()
            val countResults = results.size
            if (countResults == 0) break
            if (!callback(results)) {
                return false
            }
            page ++
        } while (count == countResults)

        return true
    }

    fun each(callback: (Map<String, Any?>) -> Boolean, count: Int = 1000): Boolean {
        return chunk(count) {
            it.forEach { row ->
                if (!callback(row))
                    return@chunk false
            }
            true
        }
    }

    protected fun enforceOrderBy() {
        if (orders.isEmpty() && unionOrders.isEmpty()) {
            throw RuntimeException("You must specify an orderBy clause when using this function.")
        }
    }

    fun pluck(column: Any): List<Any?> {
        val queryResult = get(column)
        if (empty(queryResult)) {
            return emptyList<Any?>()
        }
        val columnName = stripTableForPluck(column)
        return queryResult.map { it[columnName] }
    }

    fun implode(column: Any, glue: String = ""): String {
        return pluck(column).joinToString(glue)
    }

    fun pluck(column: Any, key: Any): Map<String, Any?> {
        val queryResult = get(column, key)
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

    protected fun stripTableForPluck(column: Any?): String? {
        if (column == null) return null
        val columnName = if (column is Expression) {
            column.value as String
        } else {
            column as String
        }
        val separator = if (columnName.contains(" as ", true)) "\\s+as\\s+" else "\\."
        return columnName.split(Regex(separator)).last()
    }

    protected fun isQueryable(value: Any?): Boolean {
        return value is Builder || value is Function1<*, *>
    }

    fun toSql(): String {
        return grammar.compileSelect(this)
    }

    fun dump(): Builder {
        println("sql:" + this.toSql())
        println("bindings" + this.getFlattenBindings().toString())
        return this
    }
    fun insert(values: Map<String, Any?>): Int {
        return insert(listOf(values))
    }

    fun insertGetId(values: Map<String, Any?>, sequence: String = "id"): Long {
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

    fun insert(values: List<Map<String, Any?>>, batch: Int = 0): Int {
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

    fun insert(sql: String, values: List<Map<String, Any?>>): Int {
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

    fun insertOrIgnore(values: Map<String, Any?>): Int {
        return insertOrIgnore(listOf(values))
    }

    fun insertOrIgnore(values: List<Map<String, Any?>>): Int {
        val sql = grammar.compileInsertOrIgnore(this, values)
        printDebugInfo(sql, values)
        return insert(sql, values)
    }

    fun update(values: Map<String, Any?>): Int {
        val sql = grammar.compileUpdate(this, values)
        val parameters = cleanBindings(grammar.prepareBindingsForUpdate(bindings, values))
        printDebugInfo(sql, parameters)
        return jdbcTemplate!!.update(sql, *parameters.toTypedArray())
    }

    fun increment(column: String, amount: Number = 1, extra: Map<String, Any?> = mutableMapOf()): Int {
        extra as MutableMap
        val wrapped = grammar.wrap(column)
        extra[column] = Expression("$wrapped + $amount")
        return update(extra)
    }

    fun decrement(column: String, amount: Number = -1, extra: Map<String, Any?> = mutableMapOf()): Int {
        extra as MutableMap
        val wrapped = grammar.wrap(column)
        extra[column] = Expression("$wrapped - $amount")
        return update(extra)
    }

    fun delete(id: Any? = null): Int {
        if (id != null) {
            where("$from.id", "=", id)
        }
        val sql = grammar.compileDelete(this)
        val parameters = cleanBindings(grammar.prepareBindingsForDelete(bindings))
        printDebugInfo(sql, parameters)
        return jdbcTemplate!!.update(sql, *parameters.toTypedArray())
    }

    fun truncate() {
        grammar.compileTruncate(this).forEach { (sql, binding) ->
            printDebugInfo(sql, binding)
            jdbcTemplate!!.update(sql, *binding.toTypedArray())
        }
    }

    public override fun clone(): Any {
        val builder = Builder()
        builder.jdbcTemplate = jdbcTemplate
        builder.grammar = grammar
        builder.processor = processor
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
        return builder
    }

    fun copy(): Builder {
        return clone() as Builder
    }
}
