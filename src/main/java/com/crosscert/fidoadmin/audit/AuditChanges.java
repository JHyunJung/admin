package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.system.SecretProps;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 엔티티의 변경 전후를 비교해 감사 메시지로 남긴다.
 *
 * <p>UPDATE 로그에 "무엇이 무엇으로 바뀌었는지"가 없으면 사후 추적이 되지 않는다.
 * 그렇다고 컬럼마다 손으로 비교문을 쓰면 엔티티가 늘 때마다 빠뜨린다.
 * 리플렉션으로 선언 필드를 훑어 바뀐 것만 추린다.
 *
 * <p><b>비밀값은 싣지 않는다.</b> 비밀번호·토큰류는 이름으로 가려내
 * 값 대신 마스크를 남긴다({@link SecretProps} 와 같은 판별을 쓴다).
 * 감사 로그는 운영자가 열람하는 화면이므로, 여기로 새면 화면에서 가린 의미가 없다.
 */
public final class AuditChanges {

    /** 한 건의 메시지가 지나치게 길어지지 않도록 값 길이를 제한한다. */
    private static final int MAX_VALUE = 120;

    /** 한 행에 담을 변경 항목 수. 넘으면 "외 N건" 으로 줄인다. */
    private static final int MAX_FIELDS = 20;

    private AuditChanges() {}

    /**
     * 변경 전 상태를 뜬다. 필드 이름 → 값 문자열.
     *
     * <p>호출자는 {@code mutator} 실행 <b>전에</b> 불러야 한다.
     * JPA 영속 엔티티를 그대로 들고 있으면 같은 객체가 바뀌므로 비교가 되지 않는다.
     */
    public static Map<String, String> snapshot(Object entity) {
        Map<String, String> values = new LinkedHashMap<>();
        if (entity == null) return values;
        for (Field f : fieldsOf(entity.getClass())) {
            values.put(f.getName(), read(f, entity));
        }
        return values;
    }

    /**
     * 변경 전후를 비교해 UPDATE 로그를 남긴다. 바뀐 것이 없으면 기록하지 않는다.
     *
     * <p>"바뀐 것이 없으면 남기지 않는다"는 의도적이다. 저장 버튼만 누른 요청까지
     * 기록하면 실제 변경이 묻힌다. 조회 사실은 DATA_VIEW 가 따로 남긴다.
     */
    public static void record(AuditLogger audit, String table, String id,
                              Map<String, String> before, Object after) {
        String diff = describe(before, snapshot(after));
        if (diff.isEmpty()) return;
        audit.log(AuditType.UPDATE, table + " UPDATE " + id + " | " + diff);
    }

    /** 변경 항목을 "필드: 전 → 후" 로 이어 붙인다. 없으면 빈 문자열. */
    static String describe(Map<String, String> before, Map<String, String> after) {
        List<String> parts = new ArrayList<>();
        int skipped = 0;
        for (Map.Entry<String, String> e : after.entrySet()) {
            String name = e.getKey();
            String now = e.getValue();
            String was = before.get(name);
            if (same(was, now)) continue;
            if (parts.size() >= MAX_FIELDS) {
                skipped++;
                continue;
            }
            parts.add(name + ": " + display(name, was) + " → " + display(name, now));
        }
        if (parts.isEmpty()) return "";
        String joined = String.join(", ", parts);
        return skipped == 0 ? joined : joined + " 외 " + skipped + "건";
    }

    /**
     * Oracle 은 빈 문자열을 NULL 로 저장한다. 폼에서 온 ""와 DB 의 null 을 다르게 보면
     * 바뀌지 않은 값이 매번 변경으로 잡힌다.
     */
    private static boolean same(String a, String b) {
        return Objects.equals(blankToNull(a), blankToNull(b));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    /** 화면에 내보낼 값. 비밀 필드는 값 대신 마스크, 없으면 "(없음)". */
    private static String display(String fieldName, String value) {
        if (value == null || value.isEmpty()) return "(없음)";
        if (SecretProps.isSecret(columnName(fieldName))) return SecretProps.MASK;
        return cut(value);
    }

    /**
     * 엔티티 필드명을 컬럼명 꼴로 바꾼다. {@code userPw} → {@code USER_PW}.
     *
     * <p>{@link SecretProps#isSecret} 은 컬럼명 규칙({@code _PW} 접미 등)으로 판별한다.
     * 필드명을 그대로 넘기면 {@code USERPW} 가 되어 걸리지 않고, 운영자 비밀번호 해시가
     * UPDATE 로그에 실린다. 낙타 표기의 대문자 앞에 밑줄을 넣어 규칙에 맞춘다.
     */
    static String columnName(String fieldName) {
        StringBuilder sb = new StringBuilder(fieldName.length() + 4);
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (Character.isUpperCase(c) && i > 0) sb.append('_');
            sb.append(Character.toUpperCase(c));
        }
        return sb.toString();
    }

    private static String cut(String s) {
        if (s.length() <= MAX_VALUE) return s;
        return s.substring(0, MAX_VALUE) + "…";
    }

    /**
     * 비교 대상 필드. 정적·합성 필드는 뺀다(Lombok·JaCoCo 가 넣는 것들이 섞인다).
     * 컬렉션과 연관 엔티티도 뺀다 — 지연 로딩을 건드리면 세션 밖에서 터지고,
     * toString 이 순환 참조로 스택을 넘길 수 있다.
     */
    private static List<Field> fieldsOf(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.isSynthetic()) continue;
                if (!isSimple(f.getType())) continue;
                f.setAccessible(true);
                fields.add(f);
            }
        }
        return fields;
    }

    /** 값으로 비교해도 안전한 타입인가. */
    private static boolean isSimple(Class<?> type) {
        return type.isPrimitive()
            || CharSequence.class.isAssignableFrom(type)
            || Number.class.isAssignableFrom(type)
            || Boolean.class == type
            || Character.class == type
            || Temporal.class.isAssignableFrom(type)
            || type.isEnum();
    }

    private static String read(Field f, Object target) {
        try {
            Object value = f.get(target);
            return value == null ? null : String.valueOf(value);
        } catch (IllegalAccessException | RuntimeException e) {
            // 값 하나를 못 읽는다고 저장을 막지 않는다. 감사 기록은 부수 작업이다.
            return null;
        }
    }
}
