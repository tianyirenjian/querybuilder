package com.tianyisoft.database.util

import kotlin.math.ceil

class Page {
    var data: List<Any> = ArrayList()
    var meta: Meta = Meta()

    companion object {
        @JvmStatic
        fun new(page: Int, pageSize: Int, total: Long, data: List<Any>): Page {
            val p = Page()
            p.data = data
            with(p.meta) {
                current = page
                size = pageSize
                this.total = total
                pages = ceil(total / pageSize.toDouble()).toInt()
            }
            return p
        }
    }
}

class Meta {
    var current: Int? = null
    var pages: Int? = null
    var size: Int? = null
    var total: Long? = null
}
