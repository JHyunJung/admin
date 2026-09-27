package com.crosscert.fidoadmin.audit;

import com.crosscert.fidoadmin.common.MenuRegistry;
import com.crosscert.fidoadmin.common.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 화면 조회를 감사 로그에 남긴다. 목록·상세 진입 한 번에 한 행이다.
 *
 * <p>기록은 {@code afterCompletion} 에서 한다. 응답이 끝난 뒤라야
 * "정말 보여 줬는가"를 알 수 있다 — 권한 오류나 예외로 끝난 요청은 조회가 아니다.
 *
 * <p>상세 화면은 어떤 레코드를 보여 줬는지가 중요하므로, 컨트롤러가
 * {@link AuditView#add} 로 대상을 적어 두면 그 건수만큼 DATA_VIEW 를 남긴다.
 * 적어 둔 것이 없으면 화면 진입 사실만 MENU_VIEW 로 남긴다.
 */
@Component
public class AuditReadInterceptor implements HandlerInterceptor {

    private final ObjectProvider<AuditLogger> audit;
    private final ObjectProvider<TenantContext> tenant;
    private final MenuRegistry menus;

    /**
     * 기록에 쓰는 협력자를 {@link ObjectProvider} 로 받는다.
     *
     * <p>이 인터셉터는 {@code WebMvcConfig} 에 등록되므로 웹 계층만 띄우는 조각
     * (@WebMvcTest)에도 딸려 온다. 그런 조각에는 {@code AuditLogger} 가 없고,
     * 생성자로 직접 받으면 <b>컨텍스트 자체가 뜨지 않는다</b>. 조회 기록은 부수 작업이라
     * 없으면 건너뛰면 될 뿐, 화면을 못 띄울 이유가 되어서는 안 된다 —
     * {@link AuditLogger} 가 "실패해도 호출자를 깨지 않는다" 고 한 것과 같은 판단이다.
     */
    public AuditReadInterceptor(ObjectProvider<AuditLogger> audit,
                                ObjectProvider<TenantContext> tenant,
                                MenuRegistry menus) {
        this.audit = audit;
        this.tenant = tenant;
        this.menus = menus;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuditLogger logger = audit.getIfAvailable();
        if (logger == null) return;
        if (!shouldRecord(request, response, ex)) return;

        String menu = menus.titleFor(request.getRequestURI());
        var targets = AuditView.targets(request);
        if (targets.isEmpty()) {
            logger.log(AuditType.MENU_VIEW, menu + " 화면 조회");
        } else {
            for (AuditView.Target target : targets) {
                logger.log(AuditType.DATA_VIEW, menu + " 상세 조회 (ID: " + target.id() + ")");
            }
        }
    }

    /**
     * 기록할 요청인가.
     *
     * <p>GET 만 본다. 변경 요청은 CREATE/UPDATE/DELETE 가 따로 남기므로
     * 여기서 또 남기면 한 동작이 두 줄이 된다.
     *
     * <p>2xx 응답만 본다. 리다이렉트(테넌트 미선택 등)는 화면을 보여 준 것이 아니다.
     */
    private boolean shouldRecord(HttpServletRequest request, HttpServletResponse response, Exception ex) {
        if (ex != null) return false;
        if (!"GET".equalsIgnoreCase(request.getMethod())) return false;
        int status = response.getStatus();
        if (status < 200 || status >= 300) return false;
        // 비동기 디스패치는 같은 요청이 두 번 들어온다. 한 번만 남긴다.
        if (!request.isAsyncStarted() && request.getDispatcherType() != jakarta.servlet.DispatcherType.REQUEST) {
            return false;
        }
        TenantContext ctx = tenant.getIfAvailable();
        return ctx != null && ctx.current().isPresent();
    }
}
