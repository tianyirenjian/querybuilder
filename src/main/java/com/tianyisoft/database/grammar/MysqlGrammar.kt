package com.tianyisoft.database.grammar

import com.tianyisoft.database.Builder

class MysqlGrammar: Grammar() {
    override val operators = listOf<String>("sounds like")

    override fun compileInsertOrIgnore(builder: Builder, values: List<Map<String, Any?>>): String {
        return compileInsert(builder, values).replaceFirst("insert", "insert ignore")
    }

    override fun compileRandom(seed: String): String {
        return "rand($seed)"
    }
}
