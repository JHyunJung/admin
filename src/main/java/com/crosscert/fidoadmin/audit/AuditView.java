package com.crosscert.fidoadmin.audit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.ui.Model;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 상세 화면이 "무엇을 보여 줬는지"를 현재 요청에 적어 두는 통로.
 * {@link AuditReadInterceptor} 가 응답을 마친 뒤 이 값을 읽어 DATA_VIEW 로 남긴다.
 *
 * <p>컨트롤러가 직접 감사 로그를 쓰지 않는 이유는 두 가지다.
 * 하나는 예외로 끝난 요청까지 "봤다"고 기록하지 않기 위해서고
 * (인터셉터는 응답 상태를 보고 판단한다),
 * 다른 하나는 화면 하나가 여러 레코드를 보여 줘도 기록 시점을 한 곳으로 모으기 위해서다.
 *
 * <p>{@code Model} 을 받는 쪽은 뷰에도 같은 값이 필요할 때를 위한 편의다.
 * 기록 자체는 요청 속성으로만 전달되므로 모델에 넣지 않아도 동작한다.
 */
public final class AuditView {

    /** 요청 속성 키. 외부에서 직접 읽지 않도록 클래스 이름을 붙여 충돌을 피한다. */
    static final String ATTRIBUTE = AuditView.class.getName() + ".TARGETS";

    private AuditView() {}

    /** 조회 대상 한 건. {@code table} 은 감사 메시지에 쓰이는 논리 이름이다. */
    public record Target(String table, String id) {}

    /** 현재 요청에 조회 대상을 더한다. 같은 대상을 두 번 더해도 두 행이 남는다. */
    public static void add(Model model, String table, String id) {
        add(table, id);
    }

    /** 모델 없이 기록만 할 때. */
    public static void add(String table, String id) {
        if (table == null || id == null) return;
        HttpServletRequest request = currentRequest();
        if (request == null) return;
        targetList(request).add(new Target(table, id));
    }

    /** 인터셉터가 읽는다. 기록이 없으면 빈 목록. */
    static List<Target> targets(HttpServletRequest request) {
        Object found = request.getAttribute(ATTRIBUTE);
        if (found instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Target> typed = (List<Target>) list;
            return Collections.unmodifiableList(typed);
        }
        return List.of();
    }

    private static List<Target> targetList(HttpServletRequest request) {
        Object found = request.getAttribute(ATTRIBUTE);
        if (found instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Target> typed = (List<Target>) list;
            return typed;
        }
        List<Target> created = new ArrayList<>();
        request.setAttribute(ATTRIBUTE, created);
        return created;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
