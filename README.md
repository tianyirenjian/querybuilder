# querybuilder

数据库增删改查构造器，使用 kotlin 编写。kotlin 调用起来非常舒服，java 也可以调用，但是因为没有默认参数所以体验略差。

### 安装

maven:

```xml
<dependency>
  <groupId>com.tianyisoft.database</groupId>
  <artifactId>querybuilder</artifactId>
  <version>1.0.4</version>
</dependency>
```

或 gradle

```
implementation 'com.tianyisoft.database:querybuilder:1.0.4'
```

### 使用说明

#### 构造实例

querybuilder 运行需要 `JdbcTemplate`, 一般 spring boot 程序里面都有，直接注入就行，自己构造也可以。

```kotlin

import com.tianyisoft.database.Builder

val builder = Builder()
builder.jdbcTemplate = jdbcTemplate

// 定义两个实例，后面示例会用到
val builder2 = Builder()
builder2.jdbcTemplate = jdbcTemplate
```

### 获取结果

#### 从表中检索所有行

可以使用 `table` 方法指定数据表， 然后通过 `get` 获取所有行

```kotlin

import org.springframework.jdbc.core.BeanPropertyRowMapper

val users = builder.table("users").get() // List<Map<String, Any?>>
val users = builder.table("users").getObject(
    BeanPropertyRowMapper(User::class.java)
) // List<User>
```

`get` 可以获取 `List<Map<String, Any?>>` 的结果集，也可以用 `getObject` 通过 `RowMapper` 获取 `List<T>` 的结果集。

#### 获取单行或单列

可以使用 `first` 方法获取第一条结果

```kotlin

import org.springframework.jdbc.core.BeanPropertyRowMapper

val user = builder.table("users").first() // Map<String, Any?>
val user = builder.table("users").firstObject(
    BeanPropertyRowMapper(User::class.java)
) // User
```

如果不想要整行数据，可以用 `value` 方法取单列的值

```kotlin
val name = builder.table("users").value("name") // 取第一个用户的 name
```

如果是使用 `id` 取单行数据，可以使用 `find` 方法

```kotlin
val user = builder.table("users").find(id)
```

同样的也有 `findObject` 方法。

#### 获取某一列的值

如果要取单列的值的集合，可以使用 `pluck` 方法

```kotlin
val names = builder.table("users").pluck("name")
```

也可以向 `pluck` 传入第二个字段，取到一个以第二个字段为键的 Map

```kotlin
val names = builder.table("users").pluck("name", "id") // { "1": "Tom", "2": "Jerry" }
```

#### 分块获取

假如数据库有成千上万条数据，可以通过 `chunk` 方法分块取出

```kotlin
builder.table("users").chunk(20) { users ->
    for (user in users) {
        println(user)
    }
    true // 必要的， 返回 true 会继续执行，若返回 false 则中断执行
}
```

#### 聚合函数

Builder 还提供了多种检索聚合值的方法，例如 `count`, `max`, `min`, `avg`, 和 `sum`

```kotlin
val count = builder.table("users").count()
val oldest = builder.table("users").max("age")
```

可以配合 `where` 使用

```kotlin
val avg = builder.table("users").where("status", "=", 1).avg("score")
```

#### 判断记录是否存在

除了通过 `count` 方法可以确定查询条件的结果是否存在之外，还可以使用 `exists` 和 `doesntExist` (`notExists`) 方法

```kotlin
if (builder.table("users").where("status", "=", 0).exists()) {
    // ...
}
```

### Select 语句

#### 指定一个 Select 语句

使用 `select` 方法可以指定要查询的列

```kotlin
val users = builder.table("users").select("id", "name", "email as user_email").get()
```

使用 `distinct` 方法可以让查询结果不重复

```kotlin
val names = builder.table("users").select("name").distinct().get()
```

### 原生表达式

有时可能需要在查询中插入任意字符串。可以使用原生表达式 `Expression`

```kotlin
val users = builder.table("users").select("id", Expression("upper(name)")).get()
```

使用 `Expression` 会直接把字符串附加到 sql 语句，因此要注意可能会有 sql 注入风险

下面有几个方法可以代替 `Expression`

`selectRaw`

```kotlin
val users = builder.table("users")
    .selectRaw("id, upper(name), score * ? as double_score", listOf(2))
    .get()
```

`whereRaw / orWhereRaw`

```kotlin
val users = builder.table("users")
    .whereRaw("score > ? and status = ?", listOf(60, 0))
    .get()
```

`havingRaw / orHavingRaw`

```kotlin
// select department, SUM(price) as total_sales from `orders` group by `department` having SUM(price) > ?
builder.table("orders")
    .selectRaw("department, SUM(price) as total_sales")
    .groupBy("department")
    .havingRaw("SUM(price) > ?", listOf(2500))
    .get();
```

`orderByRaw`

```kotlin
builder.table("users")
    .orderByRaw("updated_at - created_at DESC")
    .get()
```

`groupByRaw`

```kotlin
builder.table("users")
    .selectRaw("sex, count(*) as sex_count")
    .groupByRaw("sex")
    .get()
```

### Joins

#### inner join 语句

使用 `join` 方法可以关联其他表，可以关联多个表

```kotlin
/*
SELECT `users`.*, `contacts`.`phone`, `orders`.`price`
FROM `users`
	INNER JOIN `contacts` ON `users`.`id` = `contacts`.`user_id`
	INNER JOIN `orders` ON `users`.`id` = `orders`.`user_id`
 */
builder.table("users")
    .join("contacts", "users.id", "=", "contacts.user_id")
    .join("orders", "users.id", "=", "orders.user_id")
    .select("users.*", "contacts.phone", "orders.price")
    .get()
```

#### left join 和 right join

使用 `join` 也可以实现 left join 和 right join， 也提供了更方便的 `leftJoin` 和 `rightJoin` 方法

```kotlin
// select * from `users` as `u` left join `posts` as `p` on `u`.`id` = `p`.`user_id`
builder.table("users", "u")
    .leftJoin("posts as p", "u.id", "=", "p.user_id")
    .get()
```

#### cross join
可以使用 crossJoin 方法执行「交叉连接」。交叉连接在第一个表和被连接的表之间会生成笛卡尔积

```kotlin
builder.table("sizes")
    .crossJoin("colors")
    .get()
```

#### 高级 Join 语句

`join` 方法的第二个参数可以传闭包, 来实现更复杂的逻辑

```kotlin
/*
SELECT *
FROM `users`
	INNER JOIN `posts`
	ON `users`.`id` = `posts`.`created_by`
		OR `users`.`id` = `posts`.`updated_by`
 */
builder.table("users")
    .join("posts", {it: JoinClause ->
        it.on("users.id", "=", "posts.created_by")
            .orOn("users.id", "=", "posts.updated_by")
    })
    .get()
```
如果你想要在连接上使用 where 风格的语句，你可以在连接上使用 `JoinClause` 实例中的 `where` 和 `orWhere` 方法。这些方法会将列和值进行比较，而不是列和列进行比较：

```kotlin
/*
SELECT *
FROM `users`
	INNER JOIN `posts`
	ON `users`.`id` = `posts`.`created_by`
		AND `posts`.`status` = ?
 */

builder.table("users")
    .join("posts", {it: JoinClause ->
        it.on("users.id", "=", "posts.created_by")
            .where("posts.status", "=", 0)
    })
    .get()
```

#### 子连接查询

可以使用 `joinSub`，`leftJoinSub` 和 `rightJoinSub` 方法关联一个查询作为子查询

```kotlin
/*
SELECT *
FROM `users`
	LEFT JOIN (SELECT * FROM `posts` WHERE `status` = ?) `unpublished_posts`
	ON `users`.`id` = `unpublished_posts`.`user_id`
 */
builder2.table("posts").where("status", "=", 0)
builder.table("users")
    .leftJoinSub(builder2, "unpublished_posts", "users.id", "=", "unpublished_posts.user_id")
    .get()
```

### Unions

`union`

```kotlin
// (select `id`,`name` from `users`) union (select `id`,`title` as `name` from `posts`)
builder2.table("posts").select("id", "title as name")
builder.table("users").select("id", "name").union(builder2).get()
```

`unionAll` 和 `union` 用法一样，但是 `union` 会删除重复结果

### 基础的 Where 语句

#### where 语句

where 语句的第一个参数是列名，第二个参数是操作符，可以使用数据库支持的任意操作符，第三个参数是值, 第四个参数是连接符默认是 and，可以使用 or

例如查询年龄小于18并且积分大于60的用户

```kotlin
val users = builder.table("users")
    .where("age", "<", 18)
    .where("score", ">", 60)
    .get()
```

如果有多个条件可以使用一个嵌套 List 直接传递给 `where` 方法

```kotlin
// select * from `users` where (`age` < ? and `score` > ? and `name` like ?)
val users = builder.table("users")
    .where(listOf(listOf("age", "<", 18), listOf("score", ">", 60), listOf("name", "like", "%Tom%")))
    .get()
```

如果多个条件都是 = 操作符，可以通过一个 Map 直接传给 `where` 方法

```kotlin
// select * from `users` where (`score` = ? and `age` = ?)
val users = builder.table("users")
    .where(hashMapOf(
        "age" to 18,
        "score" to 60
    ))
    .get()
```

#### orWhere 语句

除了给 `where` 方法的最后一个参数传 or 之外，也可以直接使用 `orWhere` 方法。用法同 `where`

```
// select * from `users` where `name` = ? or (`age` < ? and `score` > ? and `name` like ?)
val users = builder.table("users")
    .where("name", "=", "Tom")
    .orWhere(listOf(listOf("age", "<", 18), listOf("score", ">", 60), listOf("name", "like", "%Tom%")))
    .get()
```

#### 其他 where 语句

`whereBetween` / `orWhereBetween` / `whereNotBetween` / `orWhereNotBetween` 验证 between 关系。意义不同，但用法都一样

```kotlin
// select * from `users` where `age` between ? and ?
val users = builder.table("users")
    .whereBetween("age", listOf(18, 60))
    .get()
```

`whereIn` / `whereNotIn` / `orWhereIn` / `orWhereNotIn` 验证 in 关系

```kotlin
// select * from `users` where `name` in (?, ?)
val users = builder.table("users")
    .whereIn("name", listOf("Tom", "Jerry"))
    .get()
```

`whereNull` / `whereNotNull` / `orWhereNull` / `orWhereNotNull`

```kotlin
// select * from `users` where `deleted_at` is null
val users = builder.table("users")
    .whereNull("deleted_at")
    .get()
```

`whereDate` / `whereMonth` / `whereDay` / `whereYear` / `whereTime` 用来比较时间

```kotlin
// select * from `users` where year(`created_at`) = ?
val users = builder.table("users")
    .whereYear("created_at", "=", 2022)
    .get()
```

`whereColumn` / `orWhereColumn` 用于比较两个列

```kotlin
// select * from `users` where `created_at` < `updated_at`
val users = builder.table("users")
    .whereColumn("created_at", "<", "updated_at")
    .get()
```

#### 逻辑分组

有时查询条件可能由复杂的 where 条件组合而成，使用 `where` 方法也可以实现。

如果查找 [（age 大于 18 小于 60）或者 （score 小于 90）] 并且 status 等于 0 的用户. (为了演示特意没有使用 whereBetween)

```kotlin
// sql: select * from `users` where ((`age` > ? and `age` < ?) or `score` < ?) and `status` = ?
// bindings: [18, 60, 90, 0]
val users = builder.table("users")
    .where({query: Builder ->
        query.where({ q: Builder ->
            q.where("age", ">", "18")
                .where("age", "<", 60)
        }).orWhere("score", "<", 90)
    })
    .where("status", "=", 0)
    .get()
```

### 高级 Where 语句

#### Where Exists 语句

`whereExists` 方法允许你使用 where exists SQL 语句

```kotlin
// select * from `users` where exists (select 1 from `posts` where `id` = `users`.`id`)
val users = builder.table("users")
    .whereExists({ query ->
        query.from("posts")
            .whereColumn("id", "=", "users.id").select(Expression("1"))
    })
    .get()
```

#### 子查询 Where 语句

有时候，你可能需要构造一个 where 子查询，将子查询的结果与给定的值进行比较。你可以通过向 `where` 方法传递闭包和值来实现此操作

```kotlin
// select * from `users` where (select count(*) from `posts` where `id` = `users`.`id`) < ?
val users = builder.table("users")
    .where({ query: Builder ->
        query.from("posts").whereColumn("id", "=", "users.id").selectRaw("count(*)")
    }, "<", 3)
    .get()
```

值也可以是子查询

```kotlin
// select * from `users` where `limit` > (select count(*) from `posts` where `id` = `users`.`id`)
val users = builder.table("users")
    .where("limit", ">", { query: Builder ->
        query.from("posts").whereColumn("id", "=", "users.id").selectRaw("count(*)")
    })
    .get()
```


太累了，停一下

coming soon ...