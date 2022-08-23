package com.tianyisoft.database.jackson

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDateTime
import java.util.*

object JsonBuilder {
    fun build(): ObjectMapper {
        val simpleModule = SimpleModule()
        simpleModule.addDeserializer(Date::class.java, DateDeserializer())
        simpleModule.addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer())
        simpleModule.addDeserializer(LocalDateTime::class.java, LocalDateTimeDeserializer())

        val objectMapper = jacksonObjectMapper()
        objectMapper.registerModule(simpleModule)
        return objectMapper
    }
}
