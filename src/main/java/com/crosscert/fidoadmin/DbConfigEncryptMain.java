package com.crosscert.fidoadmin;

import com.crosscert.fidoadmin.common.DbConfigCrypto;

public class DbConfigEncryptMain {

    public static void main(String[] args) {
        String key = System.getenv("DB_CONFIG_KEY");
        String value = System.getenv("DB_CONFIG_VALUE");

        if (value == null) {
            throw new IllegalArgumentException(
                    "DB_CONFIG_VALUE에 암호화할 값을 설정하세요."
            );
        }

        DbConfigCrypto crypto = new DbConfigCrypto(key);
        System.out.println(crypto.encrypt(value));
    }
}
