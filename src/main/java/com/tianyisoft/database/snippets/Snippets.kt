package com.tianyisoft.database.snippets

/**
 * @deprecated 程序支持了全局的软删除，不再需要这个
 */
val deletedSnippet = Snippet { builder, params ->
    if (params.isEmpty()) {
        builder.whereNotNull("deleted_at")
    } else {
        builder.whereNotNull(params[0] as String)
    }
}

val statusSnippet = Snippet { builder, params ->
    if (params.size == 1) {
        builder.where("status", "=", params[0])
    } else if (params.size == 2) {
        builder.where(params[0] as String, "=", params[1])
    }
}
