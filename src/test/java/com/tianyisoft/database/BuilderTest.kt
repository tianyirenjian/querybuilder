package com.tianyisoft.database

import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals

open class BuilderTest {
    private lateinit var builder: Builder
    private lateinit var builder2: Builder

    companion object {
        lateinit var jdbcTemplate: JdbcTemplate
        @JvmStatic
        @BeforeAll
        fun init(): Unit {
            jdbcTemplate = JdbcTemplate()
        }
    }

    @BeforeEach
    fun initBuilder() {
        builder = Builder()
        builder2 = Builder()
        builder.jdbcTemplate = jdbcTemplate
        builder2.jdbcTemplate = jdbcTemplate
    }

    @Test
    fun testShortWhere() {
        builder.table("users").where("name", "=", "Jim").whereEquals("age", 18)
        builder2.table("users").where("name", "Jim").where("age", 18)

        assertEquals(builder.toSql(), builder2.toSql())
    }

    @Test
    fun testShortWhereNotBreakWhereNull() {
        builder.table("users").where("name", "=").where("age", "!=")
        builder2.table("users").whereNull("name").whereNotNull("age")

        assertEquals(builder.toSql(), builder2.toSql())
    }

    @Test
    fun testListOfWheres() {
        builder.table("users").where({ b: Builder -> // add a closure to generate a "()" group
            b.where("name", "Jim").where("age", 18).where({ it: Builder ->
                it.where("height", ">=", 180).orWhere("height", "<=", 150)
            })
        })
        builder2.table("users").where(listOf(listOf("name", "Jim"), listOf("age", 18), listOf { it: Builder ->
            it.where("height", ">=", 180).orWhere("height", "<=", 150)
        }))

        assertEquals(builder.toSql(), builder2.toSql())
    }
}
