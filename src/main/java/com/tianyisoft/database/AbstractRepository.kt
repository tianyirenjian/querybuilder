package com.tianyisoft.database

import org.springframework.jdbc.core.JdbcTemplate
import java.util.Date

abstract class AbstractRepository {
    protected abstract val table: String
    protected abstract val dbTemplate: JdbcTemplate
    protected open val timestamps = false
    protected open val createdColumn = "created_at"
    protected open val updatedColumn = "updated_at"

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

    open fun find(id: Any): Map<String, Any?>? {
        return query().find(id)
    }

    open fun update(id: Any, data: Map<String, Any?>): Int {
        data as MutableMap
        val params = beforeUpdate(data)
        return query().where("id", "=", id).update(params).also {
            afterUpdate(id, it)
        }
    }

    open fun delete(id: Any): Int {
        return if (beforeDelete(id)) {
            query().delete(id).also {
                afterDelete(id, it)
            }
        } else 0
    }

    @JvmOverloads
    open fun query(alias: String? = null): Builder {
        return builder().table(table, alias)
    }

    protected open fun beforeInsert(params: MutableMap<String, Any?>): Map<String, Any?> {
        if (timestamps) {
            val now =  Date()
            params[createdColumn] = now
            params[updatedColumn] = now
        }
        return params
    }

    protected open fun afterInsert(id: Long) {}

    protected open fun beforeUpdate(params: MutableMap<String, Any?>): Map<String, Any?> {
        if (timestamps) {
            params[updatedColumn] = Date()
        }
        return params
    }

    protected open fun afterUpdate(id: Any, effected: Int) {}

    protected open fun beforeDelete(id: Any): Boolean = true

    protected open fun afterDelete(id: Any, effected: Int) {}
}
