package com.tianyisoft.database.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class DateDeserializer : JsonDeserializer<Date>() {
    private val format = "yyyy-MM-dd HH:mm:ss"

    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, context: DeserializationContext?): Date {
        return SimpleDateFormat(format).parse(p.valueAsString)
    }
}
