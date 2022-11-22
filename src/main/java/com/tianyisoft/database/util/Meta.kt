package com.tianyisoft.database.util

class Meta: java.io.Serializable {
    companion object {
        private const val serialVersionUID: Long = 198912190703L
    }

    var current: Int? = null
    var pages: Int? = null
    var size: Int? = null
    var total: Long? = null
}
