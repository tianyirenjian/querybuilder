package com.tianyisoft.database.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.*

class DateDeserializer: JsonDeserializer<Date>() {
    private val format = "yyyy-MM-dd HH:mm:ss"

    override fun deserialize(p0: JsonParser, p1: DeserializationContext?): Date {
        return try {
            SimpleDateFormat(format).parse(p0.valueAsString)
        } catch (e: Exception) {
            Date.from(Instant.parse(p0.valueAsString))
        }
    }
}
