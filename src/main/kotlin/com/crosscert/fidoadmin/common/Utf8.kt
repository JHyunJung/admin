package com.crosscert.fidoadmin.common

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/**
 * BYTE 의미 컬럼(`NLS_LENGTH_SEMANTICS=BYTE`, AL32UTF8)에 맞춘 문자열 길이 처리.
 *
 * 한글 1자는 UTF-8 에서 3바이트다. 문자 수로 자르면 검증을 통과한 값이 저장에서 ORA-12899 로 실패한다.
 * 바이트로 자르되 멀티바이트 문자가 경계에서 쪼개지지 않도록 문자 경계에서 끊는다.
 * 입력 검증은 [ByteSize] 가, 저장 직전 절단은 이 클래스가 맡는다.
 */
object Utf8 {

    /** `maxBytes` 를 넘지 않는 가장 긴 앞부분. null 은 null 이다. */
    @JvmStatic
    fun truncateToBytes(s: String?, maxBytes: Int): String? {
        if (s == null) return null
        // UTF-16 문자 1개는 UTF-8 로 최대 3바이트다. 길이가 한도/3 이하면 인코딩하지 않아도 한도 안이다.
        if (maxBytes >= 0 && s.length <= maxBytes / 3) return s
        val bytes = s.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size <= maxBytes) return s
        val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE)
        return try {
            decoder.decode(ByteBuffer.wrap(bytes, 0, maxBytes)).toString()
        } catch (e: CharacterCodingException) {
            s.substring(0, Math.min(s.length, maxBytes))
        }
    }
}
