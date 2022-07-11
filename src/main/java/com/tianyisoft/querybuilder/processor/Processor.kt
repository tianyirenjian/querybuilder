package com.tianyisoft.querybuilder.processor

import com.tianyisoft.querybuilder.Builder

open class Processor {
    open fun processSelect(query: Builder, results: List<Any>): List<Any> {
        return results
    }
}