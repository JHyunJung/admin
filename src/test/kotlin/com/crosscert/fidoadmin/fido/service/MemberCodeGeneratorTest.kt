package com.crosscert.fidoadmin.fido.service

import com.crosscert.fidoadmin.fido.repository.AppserverRepository
import java.util.Random
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MemberCodeGeneratorTest {

    val repo: AppserverRepository = mock()

    @Test fun producesTenUppercaseAlphanumerics() {
        val generator = MemberCodeGenerator(repo)
        for (i in 0 until 50) {
            assertThat(generator.generate()).matches("[A-Z0-9]{10}")
        }
    }

    @Test fun retriesWhenCodeAlreadyExists() {
        val generator = MemberCodeGenerator(repo, Random(1))
        whenever(repo.existsByMemberCode(anyString())).thenReturn(true, true, false)

        val code = generator.generate()

        assertThat(code).matches("[A-Z0-9]{10}")
        verify(repo, times(3)).existsByMemberCode(anyString())
    }

    @Test fun givesUpAfterFiveCollisions() {
        val generator = MemberCodeGenerator(repo, Random(1))
        whenever(repo.existsByMemberCode(anyString())).thenReturn(true)

        assertThatThrownBy { generator.generate() }.isInstanceOf(IllegalStateException::class.java)
        verify(repo, times(MemberCodeGenerator.MAX_ATTEMPTS)).existsByMemberCode(anyString())
    }
}
