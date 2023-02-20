package com.tianyisoft.database;

import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

public class BuilderJavaTest {
    private Builder builder;
    private static JdbcTemplate jdbcTemplate;

    @BeforeAll
    public static void init() {
        jdbcTemplate = new JdbcTemplate();
    }

    @BeforeEach
    public void setUp() {
        builder = new Builder();
        builder.setJdbcTemplate(jdbcTemplate);
    }

    @Test
    public void testComplexWhere() {
        String sql = builder.table("users")
                .where(new Function1<Builder, Void>() {
                    @Override
                    public Void invoke(Builder query) {
                        query.from("posts")
                                .whereColumn("id", "=", "users.id")
                                .selectRaw("count(*)");
                        return null;
                    }
                }, "<", 3)
                .toSql();
        Assertions.assertEquals("select * from `users` where (select count(*) from `posts` where `id` = `users`.`id`) < ?", sql);
    }
}
