package com.crosscert.fidoadmin.fido.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MemberCodeGeneratorTest {

    AppserverRepository repo = mock(AppserverRepository.class);

    @Test void producesTenUppercaseAlphanumerics() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo);
        for (int i = 0; i < 50; i++) {
            assertThat(generator.generate()).matches("[A-Z0-9]{10}");
        }
    }

    @Test void retriesWhenCodeAlreadyExists() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo, new Random(1));
        when(repo.existsByMemberCode(anyString())).thenReturn(true, true, false);

        String code = generator.generate();

        assertThat(code).matches("[A-Z0-9]{10}");
        verify(repo, times(3)).existsByMemberCode(anyString());
    }

    @Test void givesUpAfterFiveCollisions() {
        MemberCodeGenerator generator = new MemberCodeGenerator(repo, new Random(1));
        when(repo.existsByMemberCode(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generate).isInstanceOf(IllegalStateException.class);
        verify(repo, times(MemberCodeGenerator.MAX_ATTEMPTS)).existsByMemberCode(anyString());
    }
}
