package com.tianyisoft.database.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class DbTemplate(val value: String = "")