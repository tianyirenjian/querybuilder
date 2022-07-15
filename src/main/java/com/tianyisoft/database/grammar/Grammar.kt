package com.tianyisoft.database.grammar

import com.tianyisoft.database.Builder
import com.tianyisoft.database.Expression
import com.tianyisoft.database.JoinClause
import com.tianyisoft.database.util.empty
import com.tianyisoft.database.util.flatten
import com.tianyisoft.database.util.readInstanceProperty
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

open class Grammar {

    public open val operators = listOf<String>()

    protected var selectComponents = listOf(
        "aggregate",
        "columns",
        "from",
        "joins",
        "wheres",
        "groups",
        "havings",
        "orders",
        "limit",
        "offset"
    )

    public fun compileSelect(query: Builder): String {
        if (query.unions.isNotEmpty() && query.aggregate.isNotEmpty()) {
            return compileUnionAggregate(query)
        }
        val original = query.columns
        if (query.columns.isEmpty()) {
            query.columns = mutableListOf("*")
        }
        var sql = concatenate(
            compileComponents(query)
        )

        if (query.unions.isNotEmpty()) {
            sql = wrapUnion(sql) + " " + compileUnions(query)
        }

        query.columns = original
        return sql
    }

    protected open fun compileUnionAggregate(query: Builder): String {
        val sql = compileAggregate(query, query.aggregate)
        query.aggregate.clear()
        return "$sql from (${compileSelect(query)}) as ${wrapTable("temp_table")}"
    }

    protected open fun compileComponents(query: Builder): MutableMap<String, String> {
        val sql = mutableMapOf<String, String>()
        selectComponents.forEach { com ->
            val value: Any? = readInstanceProperty(query, com)
            if (!empty(value)) {
                val method = "compile" + com.replaceFirstChar { it.uppercase() }
                val function = this::class.memberFunctions.first { it.name == method }
                function.isAccessible = true
                sql[com] = function.call(this, query, value!!) as String
            }
        }
        return sql
    }

    protected open fun compileAggregate(query: Builder, value: Map<String, Any?>): String {
        var column = columnize(value["columns"] as List<*>)
        if (query.distinct && column != "*") {
            column = "distinct $column"
        }
        return "select ${value["function"]}($column) as aggregate"
    }

    protected open fun compileColumns(query: Builder, value: Any): String {
        value as List<*>
        if (query.aggregate.isNotEmpty()) {
            return ""
        }
        val select = if (query.distinct) {
            "select distinct "
        } else {
            "select "
        }
        return select + columnize(value)
    }

    protected open fun compileFrom(query: Builder, value: Any): String {
        return "from " + wrapTable(value)
    }

    protected open fun compileJoins(query: Builder, joins: List<JoinClause>): String {
        return joins.map { join ->
            val table = wrapTable(join.table)
            val nestedJoins = if (join.joins.isEmpty()) "" else " " + this.compileJoins(query, join.joins)
            val tableAndNestedJoins = if (join.joins.isEmpty()) table else "($table$nestedJoins)"
            "${join.type} join $tableAndNestedJoins ${compileWheres(join, 0)}".trim() // 这个 0 是乱写的，用不到
        }.joinToString(" ")
    }

    protected open fun compileWheres(query: Builder, value: Any): String {
        if (query.wheres.isEmpty()) {
            return ""
        }
        val sql = compileWheresToList(query)
        if (sql.isNotEmpty()) {
            return concatenateWhereClauses(query, sql)
        }
        return ""
    }

    protected open fun compileLimit(query: Builder, value: Any): String {
        return "limit " + value as Int
    }

    protected open fun compileOffset(query: Builder, value: Any): String {
        return "offset " + value as Int
    }

    private fun compileWheresToList(query: Builder): List<String> {
        return query.wheres.map {
            val method = "where" + it["type"]
            val function = this::class.memberFunctions.first { f -> f.name == method }
            function.isAccessible = true
            it["boolean"].toString() + " " + function.call(this, query, it) as String
        }
    }

    private fun concatenateWhereClauses(query: Builder, sql: List<String>): String {
        val conjunction = if(query is JoinClause) "on" else "where"
        return conjunction + " " + removeLeadingBoolean(sql.joinToString(" "))
    }

    private fun removeLeadingBoolean(sql: String): String {
        return sql.replaceFirst(Regex("and |or ", RegexOption.IGNORE_CASE), "")
    }

    protected open fun whereRaw(query: Builder, where: Map<String, Any?>): String {
        return where["sql"] as String
    }

    protected open fun whereBasic(query: Builder, where: Map<String, Any?>): String {
        return "${wrap(where["column"])} ${where["operator"]} ${parameter(where["value"])}"
    }

    protected open fun whereIn(query: Builder, where: Map<String, Any?>): String {
        val params = where["values"] as List<*>
        if (params.isNotEmpty()) {
            return "${wrap(where["column"])} in (${parameterize(params)})"
        }
        return "0 = 1"
    }

    protected open fun whereNotIn(query: Builder, where: Map<String, Any?>): String {
        val params = where["values"] as List<*>
        if (params.isNotEmpty()) {
            return "${wrap(where["column"])} not in (${parameterize(params)})"
        }
        return "1 = 1"
    }

    protected open fun whereNull(query: Builder, where: Map<String, Any?>): String {
        return "${wrap(where["column"])} is null"
    }

    protected open fun whereNotNull(query: Builder, where: Map<String, Any?>): String {
        return "${wrap(where["column"])} is not null"
    }

    protected open fun whereBetween(query: Builder, where: Map<String, Any?>): String {
        val between = if (where["not"] as Boolean) "not between" else "between"
        val values = where["values"] as List<Any?>
        return "${wrap(where["column"])} $between ${parameter(values.first())} and ${parameter(values.last())}"
    }

    protected open fun whereBetweenColumns(query: Builder, where: Map<String, Any?>): String {
        val between = if (where["not"] as Boolean) "not between" else "between"
        val values = where["values"] as List<*>
        return "${wrap(where["columns"] as String)} $between ${wrap(values[0])} and ${wrap(values.last())}"
    }

    protected open fun whereDate(query: Builder, where: Map<String, Any?>) = dateBasedWhere("date", query, where)
    protected open fun whereTime(query: Builder, where: Map<String, Any?>) = dateBasedWhere("time", query, where)
    protected open fun whereDay(query: Builder, where: Map<String, Any?>) = dateBasedWhere("day", query, where)
    protected open fun whereMonth(query: Builder, where: Map<String, Any?>) = dateBasedWhere("month", query, where)
    protected open fun whereYear(query: Builder, where: Map<String, Any?>) = dateBasedWhere("year", query, where)

    private fun dateBasedWhere(format: String, query: Builder, where: Map<String, Any?>): String {
        return "$format(${wrap(where["column"])}) ${where["operator"]} ${parameter(where["value"])}"
    }

    protected open fun whereColumn(query: Builder, where: Map<String, Any?>): String {
        return "${wrap(where["first"])} ${where["operator"]} ${wrap(where["second"])}"
    }

    protected open fun whereNested(query: Builder, where: Map<String, Any?>): String {
        val offset = if (query is JoinClause) 3 else 6
        // 这个 0 是随便加的，为了凑参数个数
        return "(${compileWheres(where["query"] as Builder, 0).substring(offset)})"
    }

    protected open fun whereSub(query: Builder, where: Map<String, Any?>): String {
        val select = compileSelect(where["query"] as Builder)
        return "${wrap(where["column"])} ${where["operator"]} ($select)"
    }

    protected open fun whereExists(query: Builder, where: Map<String, Any?>): String {
        return "exists (${compileSelect(where["query"] as Builder)})"
    }

    protected open fun whereNotExists(query: Builder, where: Map<String, Any?>): String {
        return "not ${whereExists(query, where)}"
    }

    protected open fun compileGroups(query: Builder, groups: List<String>): String {
        return "group by " + columnize(groups)
    }

    protected open fun compileHavings(query: Builder, havings: List<Map<String, Any?>>): String {
        val sql = havings.map { compileHaving(it) }.joinToString(" ")
        return "having " + removeLeadingBoolean(sql)
    }

    private fun compileHaving(having: Map<String, Any?>): String {
        return when(having["type"]) {
            "Raw" ->  "${having["boolean"]} ${having["sql"]}"
            "Between" -> compileHavingBetween(having)
            else -> compileBasicHaving(having)
        }
    }

    protected open fun compileBasicHaving(having: Map<String, Any?>): String {
        return "${having["boolean"]} ${wrap(having["column"])} ${having["operator"]} ${parameter(having["value"])}"
    }

    protected open fun compileHavingBetween(having: Map<String, Any?>): String {
        val between = if (having["not"] as Boolean) "not between" else "between"
        val values = having["value"] as List<Any?>
        return "${having["boolean"]} ${wrap(having["column"])} $between ${parameter(values.first())} and ${parameter(values.last())}"
    }

    protected open fun compileOrders(query: Builder, value: List<Map<String, Any?>>): String {
        if (!empty(value)) {
            return "order by " + compileOrdersToList(query, value).joinToString(", ")
        }
        return ""
    }

    private fun compileOrdersToList(query: Builder, orders: List<Map<String, Any?>>): List<String> {
        return orders.map {
            if (it.containsKey("sql")) {
                it["sql"] as String
            } else {
                "${wrap(it["column"])} ${it["direction"]}"
            }
        }
    }

    fun compileRandom(seed: String): String {
        return "RANDOM()"
    }

    protected open fun columnize(value: List<*>): String {
        return value.map { wrap(it) }.joinToString(",")
    }

    protected open fun parameter(value: Any?): String {
        return if (value is Expression) value.value as String else "?"
    }

    protected open fun  parameterize(values: List<Any?>): String{
        return values.joinToString(", ") { parameter(it) }
    }

    fun wrap(value: Any?): String? {
        if (value == null) {
            return null
        }
        if (value is Expression) {
            return value.value as String
        }
        if (value !is String) {
            return wrap(value.toString())
        }
        if (value.contains(" as ", true)) {
            return wrapAliasedValue(value)
        }
        return wrapSegments(value.split("."))
    }

    protected open fun wrapAliasedValue(value: String): String {
        val segments = value.split(Regex("\\s+as\\s+", RegexOption.IGNORE_CASE))
        return wrap(segments[0]) + " as " + wrapValue(segments[1])
    }

    protected open fun wrapSegments(segments: List<String>): String {
        return segments.mapIndexed { index, s ->
            if (index == 0 && segments.size > 1) {
                wrapTable(s)
            } else {
                wrapValue(s)
            }
        }.joinToString(".")
    }

    open fun wrapTable(table: Any): String {
        if (table is Expression) {
            return table.value as String
        }
        return wrap(table)!!
    }

    protected open fun wrapValue(value: String): String {
        if (value != "*") {
            return "`$value`"
        }
        return value
    }

    protected open fun wrapUnion(sql: String): String {
        return "($sql)"
    }

    protected open fun compileUnions(query: Builder): String {
        var sql = ""
        query.unions.forEach {
            sql += compileUnion(it)
        }

        if (query.unionOrders.isNotEmpty()) {
            sql += " " + compileOrders(query, query.unionOrders)
        }

        if (query.unionLimit != null) {
            sql += " " + compileLimit(query, query.unionLimit!!)
        }

        if (query.unionOffset != null) {
            sql += " " + compileOffset(query, query.unionOffset!!)
        }
        return sql.trimStart()
    }

    protected open fun compileUnion(union: Map<String, Any?>): String {
        val conjunction = if (union["all"] as Boolean) " union all " else " union "
        return conjunction + wrapUnion((union["query"] as Builder).toSql())
    }

    protected open fun concatenate(segments: Map<String, String>): String {
        return segments.filter { it.value != "" }.values.joinToString(" ")
    }

    open fun compileExists(builder: Builder): String {
        val select = compileSelect(builder)
        return "select exists($select) as ${wrap("exists")}"
    }

    open fun compileInsertGetId(builder: Builder, values: Map<String, Any?>, sequence: String?): String {
        return compileInsert(builder, listOf(values))
    }

    open fun compileInsert(builder: Builder, values: List<Map<String, Any?>>): String {
        val table = wrapTable(builder.from!!)
        if (values.isEmpty()) {
            return "insert into $table default values"
        }

        val columns = values.first().keys.sorted()

        val parameters: String = values.joinToString(", ") { record ->
            var sql = "("
            sql += columns.joinToString(", ") { column ->
                parameter(record[column])
            }
            "$sql)"
        }
        return "insert into $table (${columnize(columns)}) values $parameters"
    }

    open fun compileInsertOrIgnore(builder: Builder, values: List<Map<String, Any?>>): String {
        throw NotImplementedError("not implemented")
    }

    open fun compileUpdate(builder: Builder, values: Map<String, Any?>): String {
        val table = wrapTable(builder.from!!)
        val columns = values.keys.sorted().joinToString(", ") { "${wrap(it)} = ${parameter(values[it])}" }
        val where = compileWheres(builder, builder.wheres)
        return if (builder.joins.isEmpty()) {
            compileUpdateWithoutJoins(builder, table, columns, where)
        } else {
            compileUpdateWithJoins(builder, table, columns, where)
        }.trim()
    }

    protected open fun compileUpdateWithoutJoins(builder: Builder, table: String, columns: String, where: String): String {
        return "update $table set $columns $where"
    }

    protected open fun compileUpdateWithJoins(builder: Builder, table: String, columns: String, where: String): String {
        val joins = compileJoins(builder, builder.joins)
        return "update $table $joins set $columns $where"
    }

    open fun prepareBindingsForUpdate(bindings: Map<String, List<Any?>>, values: Map<String, Any?>): List<Any?> {
        val cleanBindings = bindings.toMap()
        cleanBindings as MutableMap
        cleanBindings.remove("select")
        cleanBindings.remove("join")
        val parameters = mutableListOf<Any?>()
        parameters.addAll(bindings["join"]!!)
        values.keys.sorted().forEach {
            parameters.add(values[it])
        }
        parameters.addAll(flatten(cleanBindings))
        return parameters
    }

    open fun compileDelete(builder: Builder): String {
        val table = wrapTable(builder.from!!)
        val where = compileWheres(builder, builder.wheres)
        return if (builder.joins.isEmpty()) {
            compileDeleteWithoutJoins(builder, table, where)
        } else {
            compileDeleteWithJoins(builder, table, where)
        }
    }

    private fun compileDeleteWithJoins(builder: Builder, table: String, where: String): String {
        val alias = table.split(" as ").last()
        val joins = compileJoins(builder, builder.joins)
        return "delete $alias from $table $joins $where"
    }

    protected open fun compileDeleteWithoutJoins(builder: Builder, table: String, where: String): String {
        return "delete from $table $where"
    }

    fun prepareBindingsForDelete(bindings: Map<String, List<Any?>>): List<Any?> {
        val cleanBindings = bindings.toMap()
        cleanBindings as MutableMap
        cleanBindings.remove("select")
        return flatten(cleanBindings)
    }

    fun compileTruncate(builder: Builder): Map<String, List<Any?>> {
        return hashMapOf("truncate table ${wrapTable(builder.from!!)}" to listOf())
    }
}
