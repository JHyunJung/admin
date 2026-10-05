package com.crosscert.fidoadmin.fido.service;

import com.crosscert.fidoadmin.fido.repository.AppserverRepository;
import java.security.SecureRandom;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 멤버코드(APPSERVER.MEMBER_CODE)를 만든다. 대문자·숫자 10자, 전체 고객사에서 유일.
 *
 * <p>FIDO 서버가 멤버코드를 캐시하고 연동사가 이 값을 설정에 박아 두므로, 사람이 고르지 않고
 * 한 번 정하면 바꾸지 않는다. DB 유니크 제약이 없어 존재 검사 후 다시 뽑는다.
 */
@Component
public class MemberCodeGenerator {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    static final int LENGTH = 10;
    static final int MAX_ATTEMPTS = 5;

    private final AppserverRepository repository;
    private final RandomGenerator random;

    @Autowired
    public MemberCodeGenerator(AppserverRepository repository) {
        this(repository, new SecureRandom());
    }

    MemberCodeGenerator(AppserverRepository repository, RandomGenerator random) {
        this.repository = repository;
        this.random = random;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String code = randomCode();
            if (!repository.existsByMemberCode(code)) return code;
        }
        throw new IllegalStateException("멤버코드를 만들지 못했습니다. 다시 시도해 주세요.");
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
