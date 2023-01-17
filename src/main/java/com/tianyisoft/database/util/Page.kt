package com.tianyisoft.database.util

import kotlin.math.ceil

class Page<T>: java.io.Serializable {
    var data: List<T> = ArrayList()
    var meta: Meta = Meta()

    companion object {
        private const val serialVersionUID: Long = 198912190701L

        @JvmStatic
        fun <T> new(page: Int, pageSize: Int, total: Long, data: List<T>): Page<T> {
            val p = Page<T>()
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
