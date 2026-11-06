package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.audit.AuditView
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.service.UserAccountService
import com.crosscert.fidoadmin.fido.service.UserinfoService
import java.util.regex.Pattern
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * 사용자 화면. 기본 목록은 **사용자 단위**다.
 *
 * USERINFO 는 한 행이 한 기기(크리덴셜)라 그대로 나열하면 한 사람의 기기가 여러 행으로
 * 흩어진다. 그래서 목록은 `(USERID, SERVICENAME)` 로 묶어 보여주고, 기기 목록은
 * `/users/{userid}/credentials` 로 내려간다. 기기 한 건의 상세와 상태 변경
 * (`/users/{idx}`)은 그대로 둔다 — 장애 분석에 원시 접근이 필요하다.
 *
 * `ReadOnlyController` 를 상속하지 않는다. 그쪽의 목록은 엔티티 한 행 단위
 * 페이징이라 그룹 조회에 쓸 수 없고, 상속한 채 목록만 덮어쓰면 매핑이 둘로 갈려
 * 읽는 사람이 어느 쪽이 도는지 알기 어렵다. 필요한 세 화면만 직접 만든다.
 */
@Controller
@RequestMapping("/users")
class UserController(
    private val service: UserinfoService,
    private val accounts: UserAccountService,
    private val companies: CompanyLookup,
) {

    /**
     * 사용자 단위 목록.
     *
     * 정렬은 최근 등록 역순 고정이다(`Sort.unsorted()` 를 넘겨 정렬 파라미터를
     * 무시한다). 집계 컬럼 정렬을 열어 두면 사용자 입력이 group by 절로 들어가는 경로가 생긴다.
     */
    @GetMapping
    fun list(@ModelAttribute("search") search: UserAccountSearchForm, model: Model): String {
        val page = accounts.search(search.userid, search.servicename, search.toPageable(Sort.unsorted()))
        model.addAttribute("page", page)
        model.addAttribute("searchQs", search.toQueryString())
        model.addAttribute("basePath", "/users")
        return "fido/user/list"
    }

    /**
     * 한 사용자의 기기 목록. 묶음 키가 두 컬럼이라 servicename 도 함께 받는다 —
     * userid 만으로 찾으면 다른 서비스의 동명 사용자 기기가 섞인다.
     */
    @GetMapping("/{userid}/credentials")
    fun credentials(
        @PathVariable userid: String,
        @RequestParam(required = false, defaultValue = "") servicename: String,
        model: Model,
    ): String {
        val rows = accounts.credentialsOf(userid, servicename).map { UserRow.from(it) }
        model.addAttribute("rows", rows)
        model.addAttribute("userid", userid)
        model.addAttribute("servicename", servicename)
        model.addAttribute("basePath", "/users")
        return "fido/user/credentials"
    }

    /**
     * 기기 한 건의 상세. `service.get` 이 테넌트를 검사한다(불일치는 404).
     *
     * 같은 사용자의 다른 기기도 함께 실어 준다. 한 사람이 기기를 여럿 등록하면
     * 어느 기기의 문제인지 비교해 봐야 하는데, 매번 목록으로 나갔다 들어오면
     * 어느 것을 봤는지 놓친다.
     *
     * `back` 은 돌아갈 곳이다. 이 화면에는 목록(/users)과 기기 목록
     * (/users/{userid}/credentials) 두 경로로 들어올 수 있어, 왔던 쪽으로 돌려보낸다.
     */
    @GetMapping("/{id:\\d+}")
    fun detail(
        @PathVariable id: Long,
        @RequestParam(required = false) back: String?,
        model: Model,
    ): String {
        val entity = service.get(id)
        model.addAttribute("item", UserView.from(entity))
        model.addAttribute("basePath", "/users")
        model.addAttribute("companyName", companies.name(entity.companyIdx))
        model.addAttribute("statuses", UserinfoService.STATUSES.sorted())

        // 같은 (USERID, SERVICENAME) 묶음의 기기들. 목록 화면과 같은 기준으로 묶는다.
        val devices = accounts
            .credentialsOf(entity.userid, entity.servicename ?: "")
            .map { UserRow.from(it) }
        model.addAttribute("devices", devices)
        model.addAttribute("back", safeBack(back))
        // 기기 상세는 PUBKEY·CERTIFICATE 앞부분을 보여 준다. 누가 어느 기기를 열었는지 남긴다.
        AuditView.add(model, "USERINFO", entity.idx.toString())
        return "fido/user/detail"
    }

    @PostMapping("/{id}/status")
    fun changeStatus(@PathVariable id: Long, @RequestParam status: String, redirect: RedirectAttributes): String {
        try {
            service.changeStatus(id, status)
            redirect.addFlashAttribute("flashSuccess", "상태가 변경되었습니다.")
        } catch (e: IllegalArgumentException) {
            redirect.addFlashAttribute("flashError", e.message)
        }
        return "redirect:/users/$id"
    }

    companion object {
        /**
         * 돌아갈 곳으로 받을 수 있는 유일한 형태 — 이 화면의 기기 목록 경로.
         * `credentials.html` 이 만드는 값과 같은 꼴만 통과시킨다. 그 밖(외부 주소, `javascript:`,
         * `//host`, 다른 화면)은 버리고 기본 목록으로 돌아간다. 값은 그대로 `th:href` 에 실리므로
         * 여기서 걸러야 운영자 화면에 임의 링크가 박히지 않는다.
         */
        private val SAFE_BACK: Pattern =
            Pattern.compile("/users/[^/?#]+/credentials(\\?servicename=[^/?#&]*)?")

        @JvmStatic
        internal fun safeBack(back: String?): String? {
            if (back == null || !SAFE_BACK.matcher(back).matches()) return null
            return back
        }
    }
}
