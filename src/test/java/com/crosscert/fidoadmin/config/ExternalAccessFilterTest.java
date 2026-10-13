package com.crosscert.fidoadmin.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * /api/svc/**(FIDO 서버 자가등록)과 /external/**(라이선스 수신)는 인증 없이 열려 있다.
 * 호출자는 FIDO 서버뿐이므로 접근 IP 허용 목록(CIDR)을 둘 수 있게 한다.
 * 목록이 비어 있으면(기본값) 지금처럼 모두 통과한다 — 기존 배포의 동작은 그대로다.
 */
class ExternalAccessFilterTest {

    private boolean passes(String cidrs, String path, String remoteAddr) throws Exception {
        ExternalAccessFilter filter = new ExternalAccessFilter(cidrs);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(remoteAddr);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean reached = new AtomicBoolean(false);
        FilterChain chain = (req, res) -> reached.set(true);
        filter.doFilter(request, response, chain);
        if (!reached.get()) assertThat(response.getStatus()).isEqualTo(403);
        return reached.get();
    }

    @Test void emptyAllowListLetsEveryoneThroughAsBefore() throws Exception {
        assertThat(passes("", "/api/svc/reg/fido-1", "203.0.113.9")).isTrue();
        assertThat(passes(null, "/external/license/abc", "203.0.113.9")).isTrue();
    }

    @Test void listedAddressPasses() throws Exception {
        assertThat(passes("10.0.0.0/8, 192.168.1.5", "/api/svc/reg/fido-1", "10.20.30.40")).isTrue();
        assertThat(passes("10.0.0.0/8, 192.168.1.5", "/external/license/abc", "192.168.1.5")).isTrue();
        assertThat(passes("::1", "/api/svc/deReg/fido-1", "0:0:0:0:0:0:0:1")).isTrue();
    }

    @Test void unlistedAddressIsForbidden() throws Exception {
        assertThat(passes("10.0.0.0/8", "/api/svc/reg/fido-1", "203.0.113.9")).isFalse();
        assertThat(passes("10.0.0.0/8", "/external/license/abc", "192.168.1.5")).isFalse();
    }

    @Test void otherPathsAreNotAffected() throws Exception {
        assertThat(passes("10.0.0.0/8", "/login", "203.0.113.9")).isTrue();
        assertThat(passes("10.0.0.0/8", "/users/1", "203.0.113.9")).isTrue();
    }
}
