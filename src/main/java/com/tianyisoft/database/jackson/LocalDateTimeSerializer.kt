package com.tianyisoft.database.jackson

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


class LocalDateTimeSerializer : JsonSerializer<LocalDateTime>() {
    private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    @Throws(IOException::class)
    override fun serialize(value: LocalDateTime, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.format(format))
    }
}
