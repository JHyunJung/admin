package com.crosscert.fidoadmin.audit

import jakarta.servlet.http.HttpServletRequest
import java.util.Collections
import org.springframework.ui.Model
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 상세 화면이 "무엇을 보여 줬는지"를 현재 요청에 적어 두는 통로.
 * [AuditReadInterceptor] 가 응답을 마친 뒤 이 값을 읽어 DATA_VIEW 로 남긴다.
 *
 * 컨트롤러가 직접 감사 로그를 쓰지 않는 이유는 두 가지다.
 * 하나는 예외로 끝난 요청까지 "봤다"고 기록하지 않기 위해서고
 * (인터셉터는 응답 상태를 보고 판단한다),
 * 다른 하나는 화면 하나가 여러 레코드를 보여 줘도 기록 시점을 한 곳으로 모으기 위해서다.
 *
 * `Model` 을 받는 쪽은 뷰에도 같은 값이 필요할 때를 위한 편의다.
 * 기록 자체는 요청 속성으로만 전달되므로 모델에 넣지 않아도 동작한다.
 */
object AuditView {

    /** 요청 속성 키. 외부에서 직접 읽지 않도록 클래스 이름을 붙여 충돌을 피한다. */
    @JvmField
    internal val ATTRIBUTE: String = AuditView::class.java.name + ".TARGETS"

    /** 조회 대상 한 건. `table` 은 감사 메시지에 쓰이는 논리 이름이다. */
    @JvmRecord
    data class Target(val table: String, val id: String)

    /** 현재 요청에 조회 대상을 더한다. 같은 대상을 두 번 더해도 두 행이 남는다. */
    @JvmStatic
    fun add(model: Model?, table: String?, id: String?) {
        add(table, id)
    }

    /** 모델 없이 기록만 할 때. */
    @JvmStatic
    fun add(table: String?, id: String?) {
        if (table == null || id == null) return
        val request = currentRequest() ?: return
        targetList(request).add(Target(table, id))
    }

    /** 인터셉터가 읽는다. 기록이 없으면 빈 목록. */
    @JvmStatic
    internal fun targets(request: HttpServletRequest): List<Target> {
        val found = request.getAttribute(ATTRIBUTE)
        if (found is List<*>) {
            @Suppress("UNCHECKED_CAST")
            val typed = found as List<Target>
            return Collections.unmodifiableList(typed)
        }
        return listOf()
    }

    private fun targetList(request: HttpServletRequest): MutableList<Target> {
        val found = request.getAttribute(ATTRIBUTE)
        if (found is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            return found as MutableList<Target>
        }
        val created = ArrayList<Target>()
        request.setAttribute(ATTRIBUTE, created)
        return created
    }
}

/** 현재 스레드에 묶인 HTTP 요청. 요청 밖(배치·테스트)이면 null. [AuditView]·[AuditLogger] 가 함께 쓴다. */
internal fun currentRequest(): HttpServletRequest? {
    val attrs = RequestContextHolder.getRequestAttributes()
    return if (attrs is ServletRequestAttributes) attrs.request else null
}
