package com.tianyisoft.database.exceptions

open class MultipleRecordsFoundException(count: Int) : RuntimeException("$count records were found.")
