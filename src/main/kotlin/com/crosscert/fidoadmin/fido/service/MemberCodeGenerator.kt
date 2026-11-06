package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.fido.repository.AppserverRepository
import java.security.SecureRandom
import java.util.random.RandomGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 멤버코드(APPSERVER.MEMBER_CODE)를 만든다. 대문자·숫자 10자, 전체 고객사에서 유일.
 *
 * FIDO 서버가 멤버코드를 캐시하고 연동사가 이 값을 설정에 박아 두므로, 사람이 고르지 않고
 * 한 번 정하면 바꾸지 않는다. DB 유니크 제약이 없어 존재 검사 후 다시 뽑는다.
 */
@Component
class MemberCodeGenerator internal constructor(
    private val repository: AppserverRepository,
    private val random: RandomGenerator,
) {

    @Autowired
    constructor(repository: AppserverRepository) : this(repository, SecureRandom())

    fun generate(): String {
        for (attempt in 0 until MAX_ATTEMPTS) {
            val code = randomCode()
            if (!repository.existsByMemberCode(code)) return code
        }
        throw IllegalStateException("멤버코드를 만들지 못했습니다. 다시 시도해 주세요.")
    }

    private fun randomCode(): String {
        val sb = StringBuilder(LENGTH)
        for (i in 0 until LENGTH) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)])
        }
        return sb.toString()
    }

    companion object {
        internal const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        internal const val LENGTH = 10
        internal const val MAX_ATTEMPTS = 5
    }
}
