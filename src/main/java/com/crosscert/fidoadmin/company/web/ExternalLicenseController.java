package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import com.crosscert.fidoadmin.company.repository.CcfaLicenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * FIDO 서버가 라이선스 원문을 받아 가는 경로. 이전 어드민 ExternalController 와 같다 —
 * HASHVALUE 로 찾아 LICENSE 를 평문으로 주고, 없으면 200 빈 본문. 로그인·CSRF 없이 열려 있다.
 * 고객사 구분이 없다(해시가 곧 식별자).
 */
@RestController
@RequiredArgsConstructor
public class ExternalLicenseController {

    private static final MediaType TEXT_UTF8 = new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8);

    private final CcfaLicenseRepository licenses;

    @Transactional(readOnly = true)
    @RequestMapping(value = "/external/license/{filename}", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<String> license(@PathVariable String filename) {
        String body = licenses.findFirstByHashvalueOrderByIdxAsc(filename)
            .map(CcfaLicense::getLicense)
            .orElse("");
        return ResponseEntity.ok().contentType(TEXT_UTF8).body(body == null ? "" : body);
    }
}
