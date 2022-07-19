# querybuilder

数据库增删改查构造器，使用 kotlin 编写。kotlin 调用起来非常舒服，java 也可以调用，但是因为没有默认参数所以体验略差。

### 安装

maven:

```xml
<dependency>
  <groupId>com.tianyisoft.database</groupId>
  <artifactId>querybuilder</artifactId>
  <version>1.0.3</version>
</dependency>
```

或 gradle

```
implementation 'com.tianyisoft.database:querybuilder:1.0.3'
```

### 使用说明

##### 构造实例

querybuilder 运行需要 `JdbcTemplate`, 一般 spring boot 程序里面都有，直接注入就行，自己构造也可以。

```kotlin

import com.tianyisoft.database.Builder

val builder = Builder()
builder.jdbcTemplate = jdbcTemplate
```

接下来就可以使用了。

##### 支持的方法 (部分，不然太多了)

`table`

```kotlin
builder.table("users") // from users
builder.table("users", "u") // from users as u
```

`from`, `fromSub`, `fromRaw`

from 系统方法是 table 的高级版，可以构造更复杂的语句

```kotlin
val anotherBuilder = Builder().from("users").where("status", "=", 0) // select * from users where status = 0
builder.fromSub(anotherBuilder, "su") // select * from (select * from users where status = 0) su
builder.fromRaw("(select * from users where status = ?) su", listOf(0)) // 和上面的 whereSub 相等， 但是更灵活
```

`select`, `selectRaw`, `selectSub`

`select` 要查询的列，直接传多个，不传默认为 *。 只演示 `select`。 `selectRaw` 和 `selectSub` 可以参考上面的 `fromRaw` 和 `fromSub`

```kotlin
builder.select("id", "name", "email", "address as addr") // select `id`, `name`, `email`, `address` as `addr`
```

`where`, `orWhere`, `whereNull`, `whereNotNull`

```kotlin
builder.where("votes", "=", 100) // where votes = 100
builder.where("age", ">", 30) // where age > 30
builder.where("address", "like", "%China%") // where address like '%China%'
// 可以串联
builder.where("votes", "=", 100).where("age", ">", 30).where("address", "like", "%China%")
builder.whereNull("deleted_at") // where deleted_at is null
// 使用闭包分组构造更复杂的语句
builder.table("users").where({query: Builder ->
        query.where("age", "<", "10").orWhere("age", ">", 60)
    }).whereIn("name", listOf("小明", "小华"))
// 生成的 sql 语句为： select * from `users` where (`age` < ? or `age` > ?) and `name` in (?, ?) (参数已被单独收集)
```

coming soon...