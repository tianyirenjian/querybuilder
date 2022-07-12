package com.tianyisoft.database.grammar

import com.tianyisoft.database.Builder

class MysqlGrammar: Grammar() {
    public override val operators = listOf<String>("sounds like")

    override fun compileInsertOrIgnore(builder: Builder, values: List<Map<String, Any?>>): String {
        return compileInsert(builder, values).replaceFirst("insert", "insert ignore")
    }
}
