package com.crosscert.fidoadmin.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 기존 CCFA_MANAGER.USER_PW 형식(salt 없는 SHA-256 hex 64자)과 호환되는 인코더.
 * 저장 방식 강화(salt/bcrypt)는 다른 시스템과의 호환 문제로 범위 밖이다.
 */
public class Sha256PasswordEncoder implements PasswordEncoder {

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return sha256Hex(rawPassword.toString());
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (rawPassword == null || encodedPassword == null || encodedPassword.isBlank()) {
            return false;
        }
        return encode(rawPassword).equalsIgnoreCase(encodedPassword.trim());
    }
}
