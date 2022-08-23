package com.tianyisoft.database.jackson

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime>() {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Throws(IOException::class)
    override fun deserialize(p: JsonParser, context: DeserializationContext?): LocalDateTime {
        return LocalDateTime.parse(p.valueAsString, format)
    }
}
