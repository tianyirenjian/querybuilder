package com.tianyisoft.database.annotations

import com.tianyisoft.database.util.BeanHelper
import org.springframework.context.annotation.Import

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Import(BeanHelper::class)
annotation class EnableRepository
