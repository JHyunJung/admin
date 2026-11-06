package com.crosscert.fidoadmin.fido.web

import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.common.ReadOnlyController
import com.crosscert.fidoadmin.company.service.CompanyLookup
import com.crosscert.fidoadmin.fido.entity.Challenge
import com.crosscert.fidoadmin.fido.service.ChallengeQueryService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/** 챌린지 조회. CLOB·민감 컬럼이 없어 엔티티를 그대로 뷰에 넘긴다. */
@Controller
@RequestMapping("/challenges")
class ChallengeController(
    private val service: ChallengeQueryService,
    private val companies: CompanyLookup,
) : ReadOnlyController<Challenge, Long, ChallengeSearchForm>() {

    override fun service(): CrudService<Challenge, Long, ChallengeSearchForm> = service
    override fun basePath(): String = "/challenges"
    override fun viewDir(): String = "fido/challenge"

    override fun populateDetailModel(entity: Challenge, model: Model) {
        model.addAttribute("companyName", companies.name(entity.companyIdx))
    }
}
