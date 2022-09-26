package com.tianyisoft.database

import org.springframework.jdbc.core.JdbcTemplate

abstract class AbstractRepository {
    protected abstract val table: String
    protected abstract val dbTemplate: JdbcTemplate

    open fun builder(): Builder {
        val builder = Builder()
        builder.jdbcTemplate = dbTemplate
        return builder
    }

    open fun insert(data: Map<String, Any?>): Long {
        data as MutableMap
        val params = beforeInsert(data)
        return query().insertGetId(params).also {
            afterInsert(it)
        }
    }

    open fun find(id: Long): Map<String, Any?>? {
        return query().find(id)
    }

    open fun update(id: Long, data: Map<String, Any?>): Int {
        data as MutableMap
        val params = beforeUpdate(data)
        return query().where("id", "=", id).update(params).also {
            afterUpdate(id, it)
        }
    }

    open fun delete(id: Long): Int {
        return if (beforeDelete(id)) {
            query().delete(id)
        } else 0
    }

    @JvmOverloads
    open fun query(alias: String? = null): Builder {
        return builder().table(table, alias)
    }

    open fun beforeInsert(params: MutableMap<String, Any?>): Map<String, Any?> {
        return params
    }

    open fun beforeUpdate(params: MutableMap<String, Any?>): Map<String, Any?> {
        return params
    }

    open fun afterInsert(id: Long) {}

    open fun afterUpdate(id: Long, effected: Int) {}

    open fun beforeDelete(id: Long): Boolean = true
}
