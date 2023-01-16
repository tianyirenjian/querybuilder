package com.tianyisoft.database.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.DateSerializer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.*

object JsonBuilder {
    fun build(): ObjectMapper {
        val simpleModule = SimpleModule()
        simpleModule.addSerializer(Date::class.java, DateSerializer(false, SimpleDateFormat("yyyy-MM-dd HH:mm:ss")))
        simpleModule.addDeserializer(Date::class.java, DateDeserializer())
        simpleModule.addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer())
        simpleModule.addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer())

        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModule(simpleModule)
        return objectMapper
    }
}
