package com.tianyisoft.database

import com.tianyisoft.database.annotations.DbTemplate
import com.tianyisoft.database.util.BeanHelper
import org.springframework.jdbc.core.JdbcTemplate
import java.util.Date

abstract class AbstractRepository {
    protected abstract val table: String
    protected open val timestamps = false
    protected open val createdColumn = "created_at"
    protected open val updatedColumn = "updated_at"

    open fun builder(): Builder {
        val dbTemplate = this.javaClass.getAnnotation(DbTemplate::class.java)
        val jdbcTemplate = if (dbTemplate == null || dbTemplate.value == "") {
            BeanHelper.getBean(JdbcTemplate::class.java)
        } else {
            BeanHelper.getBean(dbTemplate.value, JdbcTemplate::class.java)
        }

        val builder = Builder()
        builder.jdbcTemplate = jdbcTemplate
        return builder
    }

    open fun insert(data: Map<String, Any?>): Long {
        data as MutableMap
        val params = beforeInsert(data)
        return query().insertGetId(params).also {
            afterInsert(it)
            afterInsert(it, data)
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
            afterUpdate(id, it, data)
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
    // 比如添加完成要添加日志的话，就可以用这个，不用再查一次数据库, 下面的 update 也是
    protected open fun afterInsert(id: Long, data: Map<String, Any?>) {}

    protected open fun beforeUpdate(params: MutableMap<String, Any?>): Map<String, Any?> {
        if (timestamps) {
            params[updatedColumn] = Date()
        }
        return params
    }

    protected open fun afterUpdate(id: Any, effected: Int) {}
    protected open fun afterUpdate(id: Any, effected: Int, data: Map<String, Any?>) {}

    protected open fun beforeDelete(id: Any): Boolean = true

    protected open fun afterDelete(id: Any, effected: Int) {}
}
