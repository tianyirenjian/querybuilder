package com.tianyisoft.database

abstract class Table {
    /**
     * 增加或者更新记录时，只会更新这里指定的字段，其他字段会忽略
     *
     * 键为类属性名，值为数据库对应的字段名，如:
     * ```
     * {
     *  "name": "name",
     *  "firstName": "first_name"
     * }
     * ```
     * */
    abstract fun fillable(): Map<String, String>

    /**
     * 当所有的属性名和数据库字段名完全一致时，可能使用此方法生成 map, 省去属性名和字段名写两遍的麻烦
     */
    protected fun listToMap(columns: List<String>): Map<String, String> {
        return columns.fold(mutableMapOf()) { acc, column ->
            acc[column] = column
            acc
        }
    }

    /**
     * 当所有的属性名和数据库字段名有相同的转换规则时，可能使用此方法生成 map, 使用自定义的转换规则 [transformer] 来转换属性名到数据库字段
     */
    protected fun listToMap(columns: List<String>, transformer: (String) -> String): Map<String, String> {
        return columns.fold(mutableMapOf()) { acc, column ->
            acc[column] = transformer(column)
            acc
        }
    }
}
