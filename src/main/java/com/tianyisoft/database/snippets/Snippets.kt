package com.tianyisoft.database.snippets

val deletedSnippet = Snippet { builder, params ->
    if (params.isEmpty()) {
        builder.whereNotNull("deleted_at")
    } else {
        builder.whereNotNull(params[0] as String)
    }
}

val statusSnippet = Snippet { builder, params ->
    if (params.size == 1) {
        builder.where("status", "=", params[1])
    } else if (params.size == 2) {
        builder.where(params[0] as String, "=", params[1])
    }
}
