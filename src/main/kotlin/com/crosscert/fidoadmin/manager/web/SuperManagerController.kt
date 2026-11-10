package com.crosscert.fidoadmin.manager.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.manager.entity.CcfaManager
import com.crosscert.fidoadmin.manager.service.SuperManagerService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/**
 * /managers/super — 슈퍼관리자 계정(COMPANY_IDX = 0) 전용 화면. SYSTEM 영역이라
 * 테넌트 선택 여부와 무관하게 열린다(MenuRegistry). ManagerController 와 URL 이
 * 겹치지 않는다: "/managers/{id}" 의 {id} 는 Long 이라 "super" 는 변환에 실패해
 * ManagerController 로 넘어가지 않고, 더 구체적인 이 매핑이 먼저 선택된다.
 */
@Controller
@RequestMapping("/managers/super")
class SuperManagerController(
    private val service: SuperManagerService,
    encoder: PasswordEncoder,
) : ManagerAccountController(service, encoder) {

    override fun service(): CrudService<CcfaManager, Long, ManagerSearchForm> = service
    override fun basePath(): String = "/managers/super"
    override fun viewDir(): String = "manager/super"

    override fun populateDetailModel(e: CcfaManager, model: Model) {
        model.addAttribute("lockState", service.lockState(e.userId).orElse(null))
    }
}
