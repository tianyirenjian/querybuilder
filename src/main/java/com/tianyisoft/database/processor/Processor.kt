package com.tianyisoft.database.processor

import com.tianyisoft.database.Builder

open class Processor {
    open fun processSelect(query: Builder, results: List<Any>): List<Any> {
        return results
    }
}