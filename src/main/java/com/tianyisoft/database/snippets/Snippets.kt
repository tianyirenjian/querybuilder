package com.tianyisoft.database.snippets

val statusSnippet = Snippet { builder, params ->
    if (params.size == 1) {
        builder.where("status", "=", params[0])
    } else if (params.size == 2) {
        builder.where(params[0] as String, "=", params[1])
    }
}
