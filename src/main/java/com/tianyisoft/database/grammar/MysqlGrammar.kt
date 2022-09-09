package com.tianyisoft.database.grammar

import com.tianyisoft.database.Builder

class MysqlGrammar: Grammar() {
    override val operators = listOf<String>("sounds like")

    override fun compileInsertOrIgnore(builder: Builder, values: List<Map<String, Any?>>): String {
        return compileInsert(builder, values).replaceFirst("insert", "insert ignore")
    }

    override fun compileUpsert(
        builder: Builder,
        values: List<Map<String, Any?>>,
        update: Map<String, Any?>,
        uniqueBy: List<String>
    ): String {
        val sql = compileInsert(builder, values) + " on duplicate key update "
        val columns = update.map {
            "${wrap(it.key)} = ${parameter(it.value)}"
        }.joinToString()
        return sql + columns
    }

    override fun compileRandom(seed: String): String {
        return "rand($seed)"
    }
}
