package com.crosscert.fidoadmin.audit

import com.crosscert.fidoadmin.system.SecretProps
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.temporal.Temporal

/**
 * 엔티티의 변경 전후를 비교해 감사 메시지로 남긴다.
 *
 * UPDATE 로그에 "무엇이 무엇으로 바뀌었는지"가 없으면 사후 추적이 되지 않는다.
 * 그렇다고 컬럼마다 손으로 비교문을 쓰면 엔티티가 늘 때마다 빠뜨린다.
 * 리플렉션으로 선언 필드를 훑어 바뀐 것만 추린다.
 *
 * **비밀값은 싣지 않는다.** 비밀번호·토큰류는 이름으로 가려내
 * 값 대신 마스크를 남긴다([SecretProps] 와 같은 판별을 쓴다).
 * 감사 로그는 운영자가 열람하는 화면이므로, 여기로 새면 화면에서 가린 의미가 없다.
 */
object AuditChanges {

    /** 한 건의 메시지가 지나치게 길어지지 않도록 값 길이를 제한한다. */
    private const val MAX_VALUE = 120

    /** 한 행에 담을 변경 항목 수. 넘으면 "외 N건" 으로 줄인다. */
    private const val MAX_FIELDS = 20

    /**
     * 변경 전 상태를 뜬다. 필드 이름 → 값 문자열.
     *
     * 호출자는 `mutator` 실행 **전에** 불러야 한다.
     * JPA 영속 엔티티를 그대로 들고 있으면 같은 객체가 바뀌므로 비교가 되지 않는다.
     */
    @JvmStatic
    fun snapshot(entity: Any?): Map<String, String?> {
        val values = LinkedHashMap<String, String?>()
        if (entity == null) return values
        for (f in fieldsOf(entity.javaClass)) {
            values[f.name] = read(f, entity)
        }
        return values
    }

    /**
     * 변경 전후를 비교해 UPDATE 로그를 남긴다. 바뀐 것이 없으면 기록하지 않는다.
     *
     * "바뀐 것이 없으면 남기지 않는다"는 의도적이다. 저장 버튼만 누른 요청까지
     * 기록하면 실제 변경이 묻힌다. 조회 사실은 DATA_VIEW 가 따로 남긴다.
     */
    @JvmStatic
    fun record(audit: AuditLogger, table: String?, id: String?,
               before: Map<String, String?>, after: Any?) {
        val diff = describe(before, snapshot(after), secretFieldsOf(after))
        if (diff.isEmpty()) return
        audit.log(AuditType.UPDATE, "$table UPDATE $id | $diff")
    }

    /** 변경 항목을 "필드: 전 → 후" 로 이어 붙인다. 없으면 빈 문자열. */
    @JvmStatic
    internal fun describe(before: Map<String, String?>, after: Map<String, String?>): String =
        describe(before, after, setOf())

    /**
     * `secretFields` 는 필드명 규칙으로는 비밀임을 알 수 없는데 값이 비밀인 필드다.
     * 그 필드는 바뀌었다는 사실만 남기고 값은 마스크한다.
     */
    @JvmStatic
    internal fun describe(before: Map<String, String?>, after: Map<String, String?>, secretFields: Set<String>): String {
        val parts = ArrayList<String>()
        var skipped = 0
        for ((name, now) in after) {
            val was = before[name]
            if (same(was, now)) continue
            if (parts.size >= MAX_FIELDS) {
                skipped++
                continue
            }
            val secret = secretFields.contains(name)
            parts.add(name + ": " + display(name, was, secret) + " → " + display(name, now, secret))
        }
        if (parts.isEmpty()) return ""
        val joined = parts.joinToString(", ")
        return if (skipped == 0) joined else "$joined 외 ${skipped}건"
    }

    /**
     * Oracle 은 빈 문자열을 NULL 로 저장한다. 폼에서 온 ""와 DB 의 null 을 다르게 보면
     * 바뀌지 않은 값이 매번 변경으로 잡힌다.
     */
    private fun same(a: String?, b: String?): Boolean = blankToNull(a) == blankToNull(b)

    private fun blankToNull(s: String?): String? = if (s.isNullOrEmpty()) null else s

    /** 화면에 내보낼 값. 비밀 필드는 값 대신 마스크, 없으면 "(없음)". */
    private fun display(fieldName: String, value: String?, secret: Boolean): String {
        if (value.isNullOrEmpty()) return "(없음)"
        if (secret || SecretProps.isSecret(columnName(fieldName))) return SecretProps.MASK
        return cut(value)
    }

    /**
     * 비밀 여부가 필드명이 아니라 **행의 키**에 있는 엔티티의 비밀 필드.
     *
     * CCFA_SYSTEM_PROP 은 범용 key-value 라 `propValue` 라는 필드명만 보면 비밀값인지
     * 알 수 없다. `SMTP_PASSWORD` 행이면 값 필드가 비밀이다 — 화면([SecretProps.forDisplay])과
     * 같은 판별을 써야, 화면에서 가린 값이 감사 로그로 새지 않는다.
     */
    private fun secretFieldsOf(entity: Any?): Set<String> {
        if (entity is CcfaSystemProp && entity.id != null
            && SecretProps.isSecret(entity.id?.propKey)) {
            return setOf("propValue")
        }
        return setOf()
    }

    /**
     * 엔티티 필드명을 컬럼명 꼴로 바꾼다. `userPw` → `USER_PW`.
     *
     * [SecretProps.isSecret] 은 컬럼명 규칙(`_PW` 접미 등)으로 판별한다.
     * 필드명을 그대로 넘기면 `USERPW` 가 되어 걸리지 않고, 운영자 비밀번호 해시가
     * UPDATE 로그에 실린다. 낙타 표기의 대문자 앞에 밑줄을 넣어 규칙에 맞춘다.
     */
    @JvmStatic
    internal fun columnName(fieldName: String): String {
        val sb = StringBuilder(fieldName.length + 4)
        for (i in fieldName.indices) {
            val c = fieldName[i]
            if (Character.isUpperCase(c) && i > 0) sb.append('_')
            sb.append(Character.toUpperCase(c))
        }
        return sb.toString()
    }

    private fun cut(s: String): String {
        if (s.length <= MAX_VALUE) return s
        return s.substring(0, MAX_VALUE) + "…"
    }

    /**
     * 비교 대상 필드. 정적·합성 필드는 뺀다(Lombok·JaCoCo 가 넣는 것들이 섞인다).
     * 컬렉션과 연관 엔티티도 뺀다 — 지연 로딩을 건드리면 세션 밖에서 터지고,
     * toString 이 순환 참조로 스택을 넘길 수 있다.
     */
    private fun fieldsOf(type: Class<*>): List<Field> {
        val fields = ArrayList<Field>()
        var c: Class<*>? = type
        while (c != null && c != Any::class.java) {
            for (f in c.declaredFields) {
                if (Modifier.isStatic(f.modifiers) || f.isSynthetic) continue
                if (!isSimple(f.type)) continue
                f.isAccessible = true
                fields.add(f)
            }
            c = c.superclass
        }
        return fields
    }

    /** 값으로 비교해도 안전한 타입인가. */
    private fun isSimple(type: Class<*>): Boolean =
        type.isPrimitive ||
            CharSequence::class.java.isAssignableFrom(type) ||
            Number::class.java.isAssignableFrom(type) ||
            Boolean::class.javaObjectType == type ||
            Char::class.javaObjectType == type ||
            Temporal::class.java.isAssignableFrom(type) ||
            type.isEnum

    private fun read(f: Field, target: Any): String? {
        return try {
            val value = f.get(target)
            value?.toString()
        } catch (e: IllegalAccessException) {
            // 값 하나를 못 읽는다고 저장을 막지 않는다. 감사 기록은 부수 작업이다.
            null
        } catch (e: RuntimeException) {
            null
        }
    }
}
