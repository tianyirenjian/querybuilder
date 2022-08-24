package com.tianyisoft.database.snippets

import com.tianyisoft.database.Builder

fun interface Snippet {
    fun apply(builder: Builder, vararg params: Any?)
}