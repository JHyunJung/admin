package com.crosscert.fidoadmin.common;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * BYTE 의미 컬럼({@code NLS_LENGTH_SEMANTICS=BYTE}, AL32UTF8)에 맞춘 문자열 길이 처리.
 *
 * <p>한글 1자는 UTF-8 에서 3바이트다. 문자 수로 자르면 검증을 통과한 값이 저장에서 ORA-12899 로 실패한다.
 * 바이트로 자르되 멀티바이트 문자가 경계에서 쪼개지지 않도록 문자 경계에서 끊는다.
 * 입력 검증은 {@link ByteSize} 가, 저장 직전 절단은 이 클래스가 맡는다.
 */
public final class Utf8 {

    private Utf8() {}

    /** {@code maxBytes} 를 넘지 않는 가장 긴 앞부분. null 은 null 이다. */
    public static String truncateToBytes(String s, int maxBytes) {
        if (s == null) return null;
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) return s;
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.IGNORE)
            .onUnmappableCharacter(CodingErrorAction.IGNORE);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, 0, maxBytes)).toString();
        } catch (CharacterCodingException e) {
            return s.substring(0, Math.min(s.length(), maxBytes));
        }
    }
}
