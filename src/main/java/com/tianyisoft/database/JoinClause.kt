package com.tianyisoft.database

import com.tianyisoft.database.grammar.Grammar
import org.springframework.jdbc.core.JdbcTemplate

open class JoinClause(): Builder() {
    lateinit var type: String
    lateinit var table: Any
    lateinit var parentJdbcTemplate: JdbcTemplate
    lateinit var parentGrammar: Grammar

    constructor(parentQuery: Builder, type: String, table: Any) : this() {
        this.type = type
        this.table = table
        this.parentJdbcTemplate = parentQuery.jdbcTemplate!!
        this.parentGrammar = parentQuery.grammar

        this.jdbcTemplate = parentJdbcTemplate
        this.grammar = parentGrammar
    }

    @Suppress("UNCHECKED_CAST")
    open fun on(firstOrClosure: Any, operator: String? = null, second: String? = null, boolean: String = "and"): JoinClause {
        if (firstOrClosure is Function1<*, *>) {
            return whereNested(firstOrClosure as Function1<Builder, Unit>, boolean) as JoinClause
        }
        return this.whereColumn(firstOrClosure as String, operator, second, boolean) as JoinClause
    }

    open fun orOn(firstOrClosure: Any, operator: String? = null, second: String? = null) = on(firstOrClosure, operator, second, "or")

    override fun newQuery(): JoinClause {
        return JoinClause(newParentQuery(), type, table)
    }

    override fun forSubQuery(): Builder {
        return newParentQuery().newQuery()
    }

    protected open fun newParentQuery(): Builder {
        val builder = Builder()
        builder.jdbcTemplate = parentJdbcTemplate
        builder.grammar = parentGrammar
        return builder
    }
}
