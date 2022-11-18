package com.tianyisoft.database.util

import kotlin.math.ceil

class PageT<T>: java.io.Serializable {
    var data: List<T> = ArrayList()
    var meta: Meta = Meta()

    companion object {
        @JvmStatic
        fun <T> new(page: Int, pageSize: Int, total: Long, data: List<T>): PageT<T> {
            val p = PageT<T>()
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
