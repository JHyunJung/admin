package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.company.service.CompanyLookup;
import com.crosscert.fidoadmin.fido.service.UserAccountService;
import com.crosscert.fidoadmin.fido.service.UserinfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 사용자 화면. 기본 목록은 <b>사용자 단위</b>다.
 *
 * <p>USERINFO 는 한 행이 한 기기(크리덴셜)라 그대로 나열하면 한 사람의 기기가 여러 행으로
 * 흩어진다. 그래서 목록은 {@code (USERID, SERVICENAME)} 로 묶어 보여주고, 기기 목록은
 * {@code /users/{userid}/credentials} 로 내려간다. 기기 한 건의 상세와 상태 변경
 * ({@code /users/{idx}})은 그대로 둔다 — 장애 분석에 원시 접근이 필요하다.
 *
 * <p>{@code ReadOnlyController} 를 상속하지 않는다. 그쪽의 목록은 엔티티 한 행 단위
 * 페이징이라 그룹 조회에 쓸 수 없고, 상속한 채 목록만 덮어쓰면 매핑이 둘로 갈려
 * 읽는 사람이 어느 쪽이 도는지 알기 어렵다. 필요한 세 화면만 직접 만든다.
 */
@Controller
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserinfoService service;
    private final UserAccountService accounts;
    private final CompanyLookup companies;

    /**
     * 사용자 단위 목록.
     *
     * <p>정렬은 최근 등록 역순 고정이다({@code Sort.unsorted()} 를 넘겨 정렬 파라미터를
     * 무시한다). 집계 컬럼 정렬을 열어 두면 사용자 입력이 group by 절로 들어가는 경로가 생긴다.
     */
    @GetMapping
    public String list(@ModelAttribute("search") UserAccountSearchForm search, Model model) {
        var page = accounts.search(search.getUserid(), search.getServicename(),
            search.toPageable(Sort.unsorted()));
        model.addAttribute("page", page);
        model.addAttribute("searchQs", search.toQueryString());
        model.addAttribute("basePath", "/users");
        return "fido/user/list";
    }

    /**
     * 한 사용자의 기기 목록. 묶음 키가 두 컬럼이라 servicename 도 함께 받는다 —
     * userid 만으로 찾으면 다른 서비스의 동명 사용자 기기가 섞인다.
     */
    @GetMapping("/{userid}/credentials")
    public String credentials(@PathVariable String userid,
                              @RequestParam(required = false, defaultValue = "") String servicename,
                              Model model) {
        var rows = accounts.credentialsOf(userid, servicename).stream().map(UserRow::from).toList();
        model.addAttribute("rows", rows);
        model.addAttribute("userid", userid);
        model.addAttribute("servicename", servicename);
        model.addAttribute("basePath", "/users");
        return "fido/user/credentials";
    }

    /** 기기 한 건의 상세. {@code service.get} 이 테넌트를 검사한다(불일치는 404). */
    @GetMapping("/{id:\\d+}")
    public String detail(@PathVariable Long id, Model model) {
        var entity = service.get(id);
        model.addAttribute("item", UserView.from(entity));
        model.addAttribute("basePath", "/users");
        model.addAttribute("companyName", companies.name(entity.getCompanyIdx()));
        model.addAttribute("statuses", UserinfoService.STATUSES.stream().sorted().toList());
        return "fido/user/detail";
    }

    @PostMapping("/{id}/status")
    public String changeStatus(@PathVariable Long id, @RequestParam String status, RedirectAttributes redirect) {
        try {
            service.changeStatus(id, status);
            redirect.addFlashAttribute("flashSuccess", "상태가 변경되었습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("flashError", e.getMessage());
        }
        return "redirect:/users/" + id;
    }
}
