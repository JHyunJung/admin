package com.crosscert.fidoadmin.common;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Optional;
import java.util.regex.Pattern;

public class IpRuleListValidator implements ConstraintValidator<IpRuleList, String> {

    private static final Pattern IPV4 = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        Optional<String> bad = firstInvalid(value);
        if (bad.isEmpty()) return true;
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate("올바른 IP 형식이 아닙니다: " + escape(bad.get()))
            .addConstraintViolation();
        return false;
    }

    /** 첫 번째 잘못된 항목. 모두 맞으면 비어 있다. 공백은 모두 지운 뒤 쉼표로 나눈다. */
    public static Optional<String> firstInvalid(String value) {
        if (value == null) return Optional.empty();
        String compact = value.replaceAll("\\s+", "");
        if (compact.isEmpty()) return Optional.empty();
        for (String item : compact.split(",", -1)) {
            if (!validItem(item)) return Optional.of(item);
        }
        return Optional.empty();
    }

    private static boolean validItem(String item) {
        int slash = item.indexOf('/');
        if (slash >= 0) {
            String prefix = item.substring(slash + 1);
            if (!prefix.matches("\\d{1,2}")) return false;
            return toLong(item.substring(0, slash)) >= 0 && Integer.parseInt(prefix) <= 32;
        }
        int tilde = item.indexOf('~');
        if (tilde >= 0) {
            long start = toLong(item.substring(0, tilde));
            long end = toLong(item.substring(tilde + 1));
            return start >= 0 && end >= 0 && start <= end;
        }
        return toLong(item) >= 0;
    }

    /** IPv4 를 부호 없는 정수로. 형식이 틀리면 -1. */
    private static long toLong(String ip) {
        var m = IPV4.matcher(ip);
        if (!m.matches()) return -1;
        long v = 0;
        for (int i = 1; i <= 4; i++) {
            int octet = Integer.parseInt(m.group(i));
            if (octet > 255) return -1;
            v = (v << 8) | octet;
        }
        return v;
    }

    /** 메시지 템플릿의 EL·파라미터 기호를 무력화한다. 사용자가 넣은 값이 템플릿으로 해석되지 않게. */
    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("{", "\\{").replace("}", "\\}").replace("$", "\\$");
    }
}
