package com.crosscert.fidoadmin.system.web

import com.crosscert.fidoadmin.common.CrudController
import com.crosscert.fidoadmin.common.CrudService
import com.crosscert.fidoadmin.system.entity.CcfaFidoclient
import com.crosscert.fidoadmin.system.reload.FidoReloadService
import com.crosscert.fidoadmin.system.service.FidoClientService
import org.springframework.stereotype.Controller
import org.springframework.validation.BindingResult
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/system/fido-clients")
class FidoClientController(
    private val service: FidoClientService,
    private val reload: FidoReloadService,
) : CrudController<CcfaFidoclient, String, FidoClientForm, FidoClientSearchForm>() {

    override fun service(): CrudService<CcfaFidoclient, String, FidoClientSearchForm> = service
    override fun basePath(): String = "/system/fido-clients"
    override fun viewDir(): String = "system/fido-clients"
    override fun newForm(): FidoClientForm = FidoClientForm()
    override fun toForm(e: CcfaFidoclient): FidoClientForm = FidoClientForm.from(e)
    override fun toEntity(f: FidoClientForm): CcfaFidoclient = f.toNewEntity()
    override fun applyForm(f: FidoClientForm, e: CcfaFidoclient) { f.applyTo(e) }

    /**
     * SERVERCODE 는 CRUD 경로의 {id} 세그먼트로 그대로 쓰인다("/system/fido-clients/{code}").
     * "new" 는 등록 폼 경로("/new")와 겹치고, 점으로만 된 값은 경로 세그먼트로서 특수한 의미를 가져
     * 상세/수정/삭제 링크가 만들어지지 않는다. 문자 집합 자체는 @Pattern 으로 이미 제한했으니
     * 여기서는 예약된 값만 추가로 막는다. "reload" 는 수동 전송 경로(POST /reload)와 겹친다.
     */
    override fun validate(form: FidoClientForm, isNew: Boolean, binding: BindingResult) {
        rejectReservedPathId(form.servercode, isNew, binding, "servercode", "코드", "reload")
    }

    @PostMapping("/reload")
    fun reload(redirect: RedirectAttributes): String {
        val results = reload.reloadNow()
        val failed = results.filter { !it.ok }
        if (results.isEmpty()) {
            redirect.addFlashAttribute("flashError", "reload 를 보낼 FIDO 서버(STATUS=ON)가 없습니다.")
        } else if (failed.isEmpty()) {
            redirect.addFlashAttribute("flashSuccess", "FIDO 서버 " + results.size + "대에 reload 를 보냈습니다.")
        } else {
            val detail = failed.joinToString(", ") { it.servercode + ": " + it.detail }
            redirect.addFlashAttribute(
                "flashError",
                "FIDO 서버 " + results.size + "대 중 " + failed.size + "대 reload 실패 — " + detail,
            )
        }
        return "redirect:/system/fido-clients"
    }
}
