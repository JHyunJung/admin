package com.crosscert.fidoadmin

import com.crosscert.fidoadmin.common.DbConfigCrypto

object DbConfigEncryptMain {

    @JvmStatic
    fun main(args: Array<String>) {
        val key = System.getenv("DB_CONFIG_KEY")
        val value = System.getenv("DB_CONFIG_VALUE")
            ?: throw IllegalArgumentException(
                "DB_CONFIG_VALUE에 암호화할 값을 설정하세요."
            )

        val crypto = DbConfigCrypto(key)
        println(crypto.encrypt(value))
    }
}
