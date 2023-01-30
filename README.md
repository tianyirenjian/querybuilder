# QueryBuilder

数据库增删改查构造器，使用 kotlin 编写。kotlin 调用起来非常舒服，java 也可以调用，但是某些复杂参数的函数可能无法简单调用， 需要使用 kotlin 内部的类型。

也可以使用 kotlin 来写 Repository 层调用，然后供其他的 java 代码直接调用方法。

QueryBuilder 是我个人使用的查询库，已用在多个生产项目中，目前运行良好， 平时遇到问题我也会修改和升级程序。

程序在 jdk 1.8 上编译， 在 jdk 1.8 和 jdk 17 均测试正常运行，其他版本我没试过。

### 安装

maven:

```xml
<dependency>
  <groupId>com.tianyisoft.database</groupId>
  <artifactId>querybuilder</artifactId>
  <version>2.0.0</version>
</dependency>
```

或 gradle

```
implementation 'com.tianyisoft.database:querybuilder:2.0.0'
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

这里是公共方法，如果使用 spring boot 也可以通过后面的 `AbstractRepository` 来避免手动创建实例。

### 获取结果

#### 从表中检索所有行

可以使用 `table` 方法指定数据表， 然后通过 `get` 获取所有行

```kotlin

import org.springframework.jdbc.core.BeanPropertyRowMapper

val users = builder.table("users").get() // List<Map<String, Any?>>

val users = builder.table("users").get(User::class.java) // List<User>
```

`get` 可以获取 `List<Map<String, Any?>>` 的结果集，也可以用 `get` 通过 `KClass<T>` 或者 `Class<T>` 获取 `List<T>` 的结果集。

使用 `Class<T>` 获取对象是使用 jackson 来实现，所以如果数据库和字段名称不一致，可以使用 `@JsonProperty` 来标示.

#### 获取单行或单列

可以使用 `first` 方法获取第一条结果

```kotlin
import org.springframework.jdbc.core.BeanPropertyRowMapper

val user = builder.table("users").first() // Map<String, Any?>

val user = builder.table("users").first(User::class.java) // User
```

如果不想要整行数据，可以用 `value` 方法取单列的值

```kotlin
val name = builder.table("users").value("name") // 取第一个用户的 name
```

如果是使用 `id` 取单行数据，可以使用 `find` 方法

```kotlin
val user = builder.table("users").find(id)
```

同样 `find` 方法也有获取对象的重载方法。

可以使用 `sole` 方法来查询数据库中的有且仅有一行的数据，不存在或存在多行都会报错。

```kotlin
val user = builder.table("users").where("role", "=", "super_admin").sole()
```

与之前的 `value` 相对应的也有 `soleValue` 方法，取有且仅有一行的数据的某一列的值。

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

假如数据库有成千上万条数据，可以通过 `chunk` 方法分块取出, 闭包有两个参数，分别是分组后的数据，和当前页码，也就是第几组

```kotlin
builder.table("users").orderBy("id").chunk(20) { users, page ->
    for (user in users) {
        println(user)
    }
    true // 必要的， 返回 true 会继续执行，若返回 false 则中断执行
}
```

如果在使用 `chunk` 获取数据的同时修改数据，则 `chunk` 获取的数据会有问题，这时可以通过 `chunkById` 来分块获取, 用法和 `chunk` 一致，可以用第三个参数来设置 id 列名。

```kotlin
builder.table("users").chunkById(20, { users, page ->
    for (user in users) {
        println(user)
    }
    true // 必要的， 返回 true 会继续执行，若返回 false 则中断执行
}, "id")
```

除了使用 `chunk` 和 `chunkById` 以外，也可以使用 `each` 和 `eachById`, 相比来说，它们更进一步，分组获取后，再循环每一条数据执行操作。

`each` 和 `eachById` 闭包有两个参数，第一个是当前的单条数据，第二个是当前数据的索引，有别于 `chunk` 和 `chunkById` 的页码

```kotlin
builder.table("apps").eachById({row, index ->
    println("index $index:")
    println(row)
    true
})
```


`chunk` 和 `each` 返回的数据都是 `Map` 类型的，也可以传入 `KClass<T>` 或 `Class<T>` 方法来返回对象。 `chunkById` 和 `eachById` 暂时没有对应的方法。

```kotlin
builder.table("apps").select("id", "name").orderBy("id")
    .each(Apps::class.java, { app, index ->
        println(app)
        true
    })
```

使用 `chunk` 和 `each` 时，必须指定至少一个排序，`chunkById` 和 `eachById` 则不需要。

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
val users = builder.table("users").select("id", Expression("upper(name) as upper_name")).get()
```

使用 `Expression` 会直接把字符串附加到 sql 语句，因此要注意可能会有 sql 注入风险

下面有几个方法可以代替 `Expression`, 下面所有方法的第二个参数都可以传一个存放绑定值的 list, 也可以使用不定参数的重载方法。使用这几个方法可以避免 sql 注入风险

#### `selectRaw`

```kotlin
val users = builder.table("users")
    .selectRaw("id, upper(name), score * ? as double_score", listOf(2))
    .get()
```

#### `fromRaw`

```kotlin
// select * from (select * from users where age < ?) as u
val youngUser = builder
    .fromRaw("(users where age < ?) as u", listOf(18))
    .get()
```

#### `whereRaw / orWhereRaw`

```kotlin
val users = builder.table("users")
    .whereRaw("score > ? and status = ?", listOf(60, 0))
    .get()
```

#### `havingRaw / orHavingRaw`

```kotlin
// select department, SUM(price) as total_sales from `orders` group by `department` having SUM(price) > ?
builder.table("orders")
    .selectRaw("department, SUM(price) as total_sales")
    .groupBy("department")
    .havingRaw("SUM(price) > ?", listOf(2500))
    .get();
```

#### `orderByRaw`

```kotlin
builder.table("users")
    .orderByRaw("updated_at - created_at DESC")
    .get()
```

#### `groupByRaw`

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

当操作符是 "=" 时，绝大部分情况可以省略操作符, 当**值**是 "=", "!=" 和 "<>" 时，操作符不可以省略, 因为第三个参数默认值是 null，程序会认为是使用以上操作符与 null 比较, 此时可以使用 whereEquals.

省略 "=" 操作符时， where 方法的第三个参数不要传，或者传 null

```kotlin
val users = builder.table("users")
    .where("age", 18)
    .where("score", 60)
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

```kotlin
// select * from `users` where `name` = ? or (`age` < ? and `score` > ? and `name` like ?)
val users = builder.table("users")
    .where("name", "=", "Tom")
    .orWhere(listOf(listOf("age", "<", 18), listOf("score", ">", 60), listOf("name", "like", "%Tom%")))
    .get()
```

#### 其他 where 语句

##### `whereEquals` / `orWhereEquals`

验证相等，不用每次都传 = 号

```kotlin
// select * from `users` where `name` = ?
val users = builder.table("users")
    .whereEquals("name", "Tom")
    .get()
```

事实上，`whereEquals` 和 `orWhereEquals` 方法只是 `where` 和 `orWhere` 方法的特殊情况，只是为了更好的语义化

##### `whereBetween` / `orWhereBetween` / `whereNotBetween` / `orWhereNotBetween`

验证 between 关系。意义不同，但用法都一样

```kotlin
// select * from `users` where `age` between ? and ?
val users = builder.table("users")
    .whereBetween("age", listOf(18, 60))
    .get()
```

##### `whereIn` / `whereNotIn` / `orWhereIn` / `orWhereNotIn`

验证 in 关系

```kotlin
// select * from `users` where `name` in (?, ?)
val users = builder.table("users")
    .whereIn("name", listOf("Tom", "Jerry"))
    .get()
```

##### `whereNull` / `whereNotNull` / `orWhereNull` / `orWhereNotNull`

```kotlin
// select * from `users` where `deleted_at` is null
val users = builder.table("users")
    .whereNull("deleted_at")
    .get()
```

##### `whereNot` / `orWhereNot`

```kotlin
// select * from `users` where not `banned` = ?
val users = builder.table("users")
  .whereNot("banned", "=", 1)
  .get()
```

##### `whereDate` / `whereMonth` / `whereDay` / `whereYear` / `whereTime`

用来比较时间

```kotlin
// select * from `users` where year(`created_at`) = ?
val users = builder.table("users")
    .whereYear("created_at", "=", 2022)
    .get()
```

##### `whereColumn` / `orWhereColumn`

用于比较两个列

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


### Ordering, Grouping, Limit & Offset

#### 排序

##### `orderBy` 方法

`orderBy` 方法允许按给定列对查询结果进行排序。`orderBy` 接受的第一个参数应该是排序的列，而第二个参数确定排序的方向，可以是 `asc` 或 `desc`,默认是 `asc`

```kotlin
val users = builder.table("users")
    .orderBy("name", "asc")
    .get()
```

可以多次调用来使用多个字段排序,还有直接使用 `desc` 的 `orderByDesc` 方法

```kotlin
val users = builder.table("users")
    .orderBy("name", "asc")
    .orderBy("age", "desc")
    .orderByDesc("created_at")
    .get()
```

##### `latest` 和 `oldest` 方法

使用 `latest` 和 `oldest` 方法可以按照日期进行 `desc` / `asc` 排序，默认使用 `created_at` 字段进行排序，也可以自己传递要使用的字段

```kotlin
// select * from `users` order by `created_at` desc
val users = builder.table("users")
    .latest()
    .get()
```

##### 随机排序

使用 `inRandomOrder` 方法可以交查询结果随机排序

```kotlin
val users = builder.table("users")
    .inRandomOrder()
    .get()
```

##### 移除已存在的排序

使用 `reorder` 方法可以移除已存在的排序,也可以传递参数像使用 `orderBy` 一样重新指定排序

```kotlin
val users = builder.table("users")
    .orderBy("name", "asc")
    .orderBy("age", "desc")
    .reorder()
    .get()
```

#### 分组

##### `groupBy` 和 `having` 方法

`groupBy` 和 `having` 方法可以将查询结果分组。`having` 方法的使用方法类似于 `where` 方法。 `groupBy` 可以接受多个分组参数

```kotlin
val users = builder.table("users")
    .selectRaw("count(*) as aggregate, age")
    .groupBy("age")
    .having("aggregate", ">", 19)
    .get()
```

与 `having` 类似的还有 `havingBetween`, 使用方法类似 `whereBetween`

#### Limit 和 Offset

`limit` 和 `offset` 用来限制查询结果的返回数量或者在查询结果中跳过的数量, 还有两个方法 `take` 和 `skip` 分别是 `limit` 和 `offset` 方法的别名

```kotlin
val users = builder.table("users")
    .limit(3)
    .offset(5)
    .get()
```

#### forPage 和 paginate

`forPage` 方法内部使用 `limit` 和 `offset` 来取某一页的数据

```kotlin
val users = builder.table("users")
    .forPage(1, 3) // 第 1 页， 取 3 个
    .get()
```

`paginate` 方法是自动的分页方法，会执行两条 sql 语句，分别查总数和条目。直接返回分页对象，包括总数量，总页数等。默认当前第 1 页，每页 15条，可以通过传参数修改

```kotlin
val page = builder.table("users")
    .paginate()
```

`paginate` 返回的数据是 `Page` 类型，里面包含的是 `List<Map<String, Any?>>` 类型。也可以传递 `KClass<T>` 或 `Class<T>` 来返回对象类型

### 条件语句


有时候查询列表需要根据前台传过来的值来决定要不要使用某列进行筛选，这时可以使用 `ifTrue`, `whenTrue`, `ifFalse`, `whenFalse` 方法来处理，`if` 和 `when` 开头的方法是等价的

```kotlin
val status = request.getParameter("status")

val users = builder.table("users")
    .whenTrue(status != null, { query ->
        query.where("status", "=", status)
    })
    .get()
```

这些方法还有第三个参数，当条件不成立的时候，不会执行第二个参数，而是执行第三个。

```kotlin
val deleted = false // 我这时定义死了

val users = builder.table("users")
    .whereTrue(deleted, {
        it.whereNotNull("deleted")
    }, {
        it.whereNull("deleted") // 会执行这一条，就像 if/else 一样
    })
    .get()
```

### 插入

虽然是叫 querybuilder, 但是也支持简单的增删改, ^_^。

`insert`, `insertGetId` 和 `insertOrIgnore` 用于给数据库插入记录

`insert` 支持单条插入和多条插入，多条插入还可以设置分批插入,单条插入的参数是 `Map<String, Any?>` 类型，多条插入则是 `List<Map<String, Any?>>`，返回的是插入成功的条数

自 1.0.7 版本开始，支持传入 `com.tianyisoft.database.Table` 子类的实例, `Table` 类通过实现 `fillable` 方法来控制要添加或修改的字段, 具体可见 `Table` 类源码

```kotlin
val rows = builder.table("users")
    .insert(hashMapOf(
        "name" to "tom",
        "age" to 20,
        "created_at" to Date()
    ))
```

`insertGetId` 和单条插入时的 `insert` 方法一致，但是返回的是自增的 id， 如果自增字段不是 id，可以通过第二个参数设置

`insertOrIgnore` 会忽略错误, 用法和 `insert` 一致

### 修改

`update` 用于更新数据,参数是 `Map<String, Any?>` 类型，更新可以使用表达式，比如给某列加 1

```kotlin
builder.table("users")
    .where("id", "=", 3)
    .update(hashMapOf(
        "name" to "Jerry",
        "age" to Expression("age + 1")
    ))
```

`increment` 和 `decrement` 用于增加或减少指定字段的值，内部使用的就是 `update` 方法加 `Expression` 表达式

```
builder.table("users")
    .where("id", "=" 3)
    .increment("age", 2) // 加两岁
```

### 删除

`delete` 方法可以删除表的记录，可以一次删除一条或多条

```kotlin
builder.table("users")
    .delete(3)
```

或

```kotlin
builder.table("users")
    .where("id", ">", 30)
    .delete()
```

### 清空表

`truncate` 方法用来清空表，不过不建议使用，清空表这种高风险的操作，还是手动操作比较好

```kotlin
builder.table("users").truncate()
```

### 调试

可以打印出当前的 sql 语句和绑定的数据，用来判断逻辑是否正确

```kotlin
builder.table("users").where("id", "=", 3).dump()
```

以上程序会打印出

```text
sql: select * from `users` where `id` = ?
bindings: [3]
```

### AbstractRepository

如果你使用的是 spring boot, QueryBuilder 提供了一个 `AbstractRepository` 类

**首先在 spring boot 的 application 类上添加注解 `@EnableRepository`**， 然后就可以继承该类，并添加 `@Component` 就可以注入使用了

继承 `AbstractRepository` 需要提供表名。

```kotlin
import com.tianyisoft.database.AbstractRepository
import org.springframework.stereotype.Component
import com.tianyisoft.database.util.Page

@Component
open class UserRepository: AbstractRepository() {
    override val table: String = "users"

    // 获取未删除用户的分页数据
    fun notDeleted(): Page<Map<String, Any?>> {
        return query().whereNull("deleted_at").orderBy("id").paginate()
    }
}
```

#### 多数据源

QueryBuilder 本身就可以通过设置不同的 JdbcTemplate 来实现多数据源。使用 `AbstractRepository` 时，可以通过 `@DbTemplate` 来指定使用哪个数据源, 默认使用 spring boot 默认的 JdbcTemplate bean.

#### 基本方法

`AbstractRepository` 也实现了简单的增删改查方法, 同时也可以通过 `query()` 方法使用更多的 querybuilder 提供的方法。

```kotlin
val id = userRepository.insert(hashMapOf(/*...*/))
userRepository.find(id)
userRepository.update(id, hashMapOf(/*...*/))
userRepository.delete(id)
userRepository.query().where("id", ">", 3).orWhere("age", "<", 10).get()
```

`AbstractRepository` 还提供了简单的 `beforeInsert`， `afterInsert`, `beforeUpdate`, `afterUpdate`, `beforeDelete` 和 `afterDelete` 方法用于在操作数据前后做一些操作. 可以通过继承方法使用它们。

比如增加数据前要设置 `created_at` 和 `updated_at` 的值。

这个功能只对 `AbstractRepository` 自有的方法有作用，通过 `query()` 调用的操作不起作用

```kotlin
override fun beforeInsert(params: MutableMap<String, Any?>): Map<String, Any?> {
    val now = Date()
    params["created_at"] = now
    params["updated_at"] = now
    return params
}
```

上面提到了设置 `created_at` 和 `updated_at` 的值, 还有更直接的办法来完成这个操作，继承 `AbstractRepository` 并重写父类的 `timestamps` 值为 `true` 就可以自动插入这两列的值，还可能通过重写 `createdColumn` 和 `updatedColumn` 来修改字段名。

同样的，这个功能只对 `AbstractRepository` 自有的方法有作用，通过 `query()` 调用的操作不起作用

如果习惯使用 java 写程序，可以使用 kotlin 来实现 Repository, 然后通过 java 代码来调用，这样就只有 Repository 一层是 kotlin 代码。因为使用 kotlin 来调用 querybuilder 实在是太舒服了.

### Snippet 代码片段

有时相同的一些查询条件会多次使用，这时可以写成代码片段，然后在使用时通过 `use` 方法调用

使用代码片段的方法是实现 `Snippet` 接口,比如常用的查询状态的条件可以写一个代码片段

```kotlin
class StatusSnippet : Snippet {
    override fun apply(builder: Builder, vararg params: Any?) {
        if (params.size == 1) { // 如果有一个参数，直接查询 status
            builder.where("status", "=", params[0])
        } else if (params.size == 2) { // 如果有两个参数，则第一个是状态的字段名
            builder.where(params[0] as String, "=", params[1])
        }
    }
}

// 或者直接生成一个对象, 是一样的

val statusSnippet = Snippet { builder, params ->
    if (params.size == 1) {
        builder.where("status", "=", params[0])
    } else if (params.size == 2) {
        builder.where(params[0] as String, "=", params[1])
    }
}

```

这样就创建完一个代码段了，在需要的时候直接使用 `use` 方法，并传入参数即可

```kotlin
builder.table("users").use(StatusSnippet(), 1).get()
// 或者当参数名不叫 status 时, 比如叫 state
builder.table("users").use(StatusSnippet(), "state", 1).get()
```

实际上 statusSnippet 已经提供了，可以直接使用

### 软删除

QueryBuilder 提供了软删除的功能，可以通过 `enableSoftDelete` 方法来开启，支持定义软删除的字段和类型，目前支持时间和数字两种类型

```kotlin
builder.table("users").enableSoftDelete().get() // 默认是 deleted_at 字段，类型是时间

builder.table("users").enableSoftDelete("deleted_at", DeletedDataType.DATETIME).get() // 指定字段名和类型

builder.table("users").enableSoftDelete("deleted", DeletedDataType.INTEGER).get() // 指定字段名和类型, 类型为数字型
```

时间类型会在 sql 中使用 `is null` 来判断是否删除，数字类型会使用 `= 0` 来判断是否删除

软删除会对查询，修改和删除操作起作用， 开启了软删除后，查询和修改会自动加上软删除的条件，删除会自动更新软删除字段的值

### Relation 关联

因为 QueryBuilder 并不是 orm 框架，所以实现关联也没有和类的字段关联起来，而是直接生成一个关联的实例来使用。

目前直接的关联有 `HasOne`(一对一), `BelongsTo`(反向一对一), `HasMany`(一对多) 和 `BelongsToMany`(多对多)。

#### HasOne, HasMany 和 BelongsTo

##### HasOne 和 HasMany

首先创建两个表

`user` 表

| id  | name	 | age | habits            |
|----:|------:|----:|:------------------|
| 1	  | 小米	   | 18	 | ["sports"]        |
| 2	  | 小明	   | 22	 | ["music"]         |
| 3	  | 小红	   | 21	 | ["sleep", "read"] |

`id_card` 表

| 	id | 	user_id	 | number             |
|----:|----------:|:-------------------|
| 	1	 |        1	 | ABSBEE2022-02-28   |
| 	2	 |        2	 | 123456200105021251 |

每个 user 有一个 id_card, 可以表示为 `HasOne("id_card", "user_id", "id")`, 因为默认参数可以简写为 `HasOne("id_card", "user_id")`

查询关联数据使用 `with` 方法, `with` 方法的第一个参数是关联字段的名字，是任意取的，第二个参数是关联对象

```kotlin
val userWithIdCard = builder.table("user")
    .with("id_card", HasOne("id_card", "user_id"))
    .get() // List<Map<String, Any?>>
```

以上程序返回

```json
[
    {
        "id":1,
        "name":"小米",
        "age":18,
        "habits":"[\"sports\"]",
        "id_card":{
            "id":1,
            "user_id":1,
            "number":"ABSBEE2022-02-28"
        }
    },
    {
        "id":2,
        "name":"小明",
        "age":22,
        "habits":"[\"music\"]",
        "id_card":{
            "id":2,
            "user_id":2,
            "number":"123456200105021251"
        }
    },
    {
        "id":3,
        "name":"小红",
        "age":21,
        "habits":"[\"sleep\", \"read\"]",
        "id_card":null
    }
]
```

这里也可以创建一个 `User` 类和 `IdCard` 类来接收参数

```java
class User {
  private Long id;
  private String name;
  private Integer age;
  private String habits;
  @JsonProperty("id_card")
  private Idcard idCard;
  // getters and setters ...
}

class IdCard {
  private Long id;
  @JsonProperty("user_id")
  private Long userId;
  private String number;
  // getters and setters ...
}
```

取的时候使用 `get` 方法的重载方法就可以了。

```kotlin
val userWithIdCard = builder.table("user")
    .with("id_card", HasOne("id_card", "user_id"))
    .get(User::class.java) // List<User>
```

以上是 `HasOne` 的用法，`HasOne` 是 `HasMany` 的一种特殊情况，`HasMany` 和 `HasOne` 用法基本一样, 不再赘述。

`HasMany` 返回的是数组，而不像 `HasOne` 是单独的对象，这是唯一的区别。

##### BelongsTo

`BelongsTo` 是和 `HasOne` 和 `HasMany` 相反的关联，还用上面的两个表举例, id_card 是属于 user 的，用 `BelongsTo` 表示就是 `BelongsTo("user", "user_id")`

```kotlin
val idCardWithUser = builder.table("id_card")
    .with("user", BelongsTo("user", "user_id"))
    .get(IdCard::class.java)
println(idCardWithUser)
```

输出数据

```text
[IdCard{id=1, userId=1, number='ABSBEE2022-02-28', user=User{id=1, name='小米', age=18, habits='["sports"]'}}, IdCard{id=2, userId=2, number='123456200105021251', user=User{id=2, name='小明', age=22, habits='["music"]'}}]
```

`HasOne`, `HasMany` 和 `BelongsTo` 都是继承自 `Builder` 的，可以使用 `Builder` 类的各种方法。

如查询 user, 把 id_card 也带出来，同时限制 id_card 的 id 是大于 1 的。

```kotlin
builder.table("user")
    .with("id_card", HasOne("id_card", "user_id").where("id", ">", 1) as HasOne)
    .get()
```

#### BelongsToMany

`BelongsToMany` 表示多对多关系，需要中间表的支持。比如用户和权限的关系，一个用户可以有多个权限，一个权限也可以属于多个用户。

下面有3个表，用户表，权限表，用户权限关系表

`user` 表

| id  | name	 |
|----:|------:|
| 1	  | 小米	   |
| 2	  | 小明	   |
| 3	  | 小红	   |

`permission` 表

|  id | name	 |
|----:|------:|
|  1	 |    增	 |
|  2	 |    删	 |
|  3	 |    改	 |
|  4	 |    查	 |

`permission_user` 表

| 	id | 	user_id | 	permission_id |
|----:|---------:|---------------:|
| 	1	 |       1	 |              1 |
| 	2	 |       1	 |              2 |
| 	3	 |       1	 |              3 |
| 	4	 |       2	 |              4 |
| 	5	 |       1	 |              4 |
| 	6	 |       3	 |              4 |
| 	7	 |       2	 |              1 |

user 表和 permission 表的关联可以表示为 `BelongsToMany("permission", "permission_user", "user_id", "permission_id", "id", "id")`, 
第一个参数为关联的表名，第二个参数为中间表名，第三个参数为当前表在中间表里的关联字段，
第四个参数为关联的表在中间表的关联字段, 第五个参数为当前表的id字段，第六个参数为关联表的id字段。
当第五个和第六个参数是 id时，可以省略

反向的 permission 表和 user 表的关系可以表示为`BelongsToMany("user", "permission_user", "permission_id", "user_id")`

接下来就可以通过 `with` 方法来取数据了,使用方法和上面的 `HasOne` 没有区别。

`BelongsToMany` 也继承自 `Builder` 类，所以也可以使用 `Builder` 类的各种方法，除此之外，还实现了自己的一些方法用来限制中间表。

这些方法是 `wherePivot`, `wherePivotBetween`, `wherePivotIn`, `wherePivotNull`, `orderByPivot`,这些限制的是中间表的数据, 用法和去掉 Pivot 后的 `Builder` 类同名方法一样，如

```kotlin
BelongsToMany("permission", "permission_user", "user_id", "permission_id", "id", "id")
    .wherePivot("id", ">", 3)
// 要求中间表 permission_user 的数据 id 是大于 3的，不符合条件的数据则不会被关联出来
```

#### 关联统计

关联可以不直接查出关联数据，而是计算出关联的数据，比如关联的条数，关联内容某列的和等。

关联统计的方法有 `withCount`, `withSum`, `withAvg`, `withMin` 和 `withMax`

还以上面的 `HasOne` 为例, 查询每个 user 的 id_card 数量:

```kotlin
val user = builder.table("user")
    .select("id", "name") // 这里的 select 是有用的，不然会只返回 id_card_count 一列
    .withCount("id_card_count", HasOne("id_card", "user_id"))
    .get()
```

以上代码返回

```json
[
    {
        "id":1,
        "name":"小米",
        "id_card_count":1
    },
    {
        "id":2,
        "name":"小明",
        "id_card_count":1
    },
    {
        "id":3,
        "name":"小红",
        "id_card_count":0
    }
]
```

#### 基于关联存在的查询

可以把关联做为条件来进行查询，比如我要查询有 id_card 的 user, 或者要查询有三个及以上 permission 的 user。

要实现这个的功能可以使用 whereHas

```kotlin
// 查询有 id_card 的 user
/*
SELECT *
FROM `user`
WHERE EXISTS (
	SELECT 1
	FROM `id_card`
	WHERE `id_card`.`user_id` = `user`.`id`
)
*/
builder.table("user").whereHas(HasOne("id_card", "user_id")).get()


// 查询有三个及以上 permission 的 user
/*
SELECT *
FROM `user`
WHERE (
	SELECT count(*)
	FROM `permission`
		INNER JOIN `permission_user` ON `permission_user`.`permission_id` = `permission`.`id`
	WHERE `permission_user`.`user_id` = `user`.`id`
) >= ?
*/
builder.table("user")
    .whereHas(BelongsToMany("permission", "permission_user", "user_id", "permission_id"), ">=", 3)
    .get()
```


### 结语

上面只是各个方法的简介，更复杂的用法也可能没有提到，可以翻看源码了解。另外，有用的方法会一直增加，文档也会一直完善。
