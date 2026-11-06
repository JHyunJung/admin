package com.crosscert.fidoadmin.common

import com.crosscert.fidoadmin.company.entity.CcfaLicense
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ui.ExtendedModelMap
import org.springframework.validation.BeanPropertyBindingResult
import org.springframework.validation.BindingResult
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap

class CrudControllerValidateHookTest {

    /** BindingResult.rejectValue 는 빈 프로퍼티(getter/setter)를 요구한다. 테스트 코드는 Lombok 을 쓰지 않는다. */
    class Form {
        var name: String? = null
    }

    val service: CrudService<CcfaLicense, Long, SearchForm> = mock()

    /** validate() 가 오류를 넣으면 서비스는 호출되지 않고 폼 뷰로 돌아간다. */
    val controller: CrudController<CcfaLicense, Long, Form, SearchForm> = object : CrudController<CcfaLicense, Long, Form, SearchForm>() {
        override fun service(): CrudService<CcfaLicense, Long, SearchForm> = this@CrudControllerValidateHookTest.service
        override fun basePath(): String = "/x"
        override fun viewDir(): String = "x/x"
        override fun newForm(): Form = Form()
        override fun toForm(entity: CcfaLicense): Form = Form()
        override fun toEntity(form: Form): CcfaLicense = CcfaLicense()
        override fun applyForm(form: Form, entity: CcfaLicense) {}
        override fun validate(form: Form, isNew: Boolean, binding: BindingResult) {
            if (isNew && form.name == null) binding.rejectValue("name", "required", "이름은 필수입니다.")
        }
    }

    @Test fun createStopsWhenValidateRejects() {
        val form = Form()
        val binding: BindingResult = BeanPropertyBindingResult(form, "form")
        val view = controller.create(form, binding, ExtendedModelMap(), RedirectAttributesModelMap())
        assertThat(view).isEqualTo("x/x/form")
        assertThat(binding.getFieldError("name")!!.defaultMessage).isEqualTo("이름은 필수입니다.")
        verify(service, never()).create(anyOrNull())
    }

    /** 서비스가 업무 규칙으로 등록을 거부하면 500 이 아니라 사유를 폼 오류로 돌려준다(update 와 같다). */
    @Test fun createShowsBusinessRuleRejectionOnForm() {
        val form = Form()
        form.name = "x"
        val binding: BindingResult = BeanPropertyBindingResult(form, "form")
        whenever(service.create(any())).thenThrow(IllegalStateException("이미 처리된 신청입니다."))
        val view = controller.create(form, binding, ExtendedModelMap(), RedirectAttributesModelMap())
        assertThat(view).isEqualTo("x/x/form")
        assertThat(binding.globalError!!.defaultMessage).isEqualTo("이미 처리된 신청입니다.")
    }

    @Test fun updateSkipsIsNewOnlyRule() {
        val form = Form()
        val binding: BindingResult = BeanPropertyBindingResult(form, "form")
        val view = controller.update(9L, form, binding, ExtendedModelMap(), RedirectAttributesModelMap())
        assertThat(view).isEqualTo("redirect:/x/9")
        verify(service).update(anyOrNull(), anyOrNull())
    }
}
