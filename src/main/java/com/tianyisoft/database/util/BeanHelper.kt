package com.tianyisoft.database.util

import org.springframework.beans.factory.InitializingBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

@Component
class BeanHelper : ApplicationContextAware, InitializingBean {
    private lateinit var applicationContext: ApplicationContext

    override fun setApplicationContext(applicationContext: ApplicationContext) {
        this.applicationContext = applicationContext
    }

    override fun afterPropertiesSet() {
        beanHelper = this
    }

    companion object {
        private var beanHelper: BeanHelper? = null
        fun <T> getBean(clazz: Class<T>): T {
            return beanHelper!!.applicationContext!!.getBean(clazz)
        }

        fun getBean(name: String): Any {
            return beanHelper!!.applicationContext!!.getBean(name)
        }

        fun <T> getBean(name: String, clazz: Class<T>): T {
            return beanHelper!!.applicationContext!!.getBean(name, clazz)
        }
    }
}