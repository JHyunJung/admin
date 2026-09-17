package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class CrudControllerValidateHookTest {

    /** BindingResult.rejectValue 는 빈 프로퍼티(getter/setter)를 요구한다. 테스트 코드는 Lombok 을 쓰지 않는다. */
    public static class Form {
        private String name;
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    @SuppressWarnings("unchecked")
    CrudService<CcfaLicense, Long, SearchForm> service = mock(CrudService.class);

    /** validate() 가 오류를 넣으면 서비스는 호출되지 않고 폼 뷰로 돌아간다. */
    CrudController<CcfaLicense, Long, Form, SearchForm> controller = new CrudController<>() {
        @Override protected CrudService<CcfaLicense, Long, SearchForm> service() { return service; }
        @Override protected String basePath() { return "/x"; }
        @Override protected String viewDir() { return "x/x"; }
        @Override protected SearchForm newSearchForm() { return new SearchForm(); }
        @Override protected Form newForm() { return new Form(); }
        @Override protected Form toForm(CcfaLicense e) { return new Form(); }
        @Override protected CcfaLicense toEntity(Form f) { return new CcfaLicense(); }
        @Override protected void applyForm(Form f, CcfaLicense e) {}
        @Override protected void validate(Form form, boolean isNew, BindingResult binding) {
            if (isNew && form.getName() == null) binding.rejectValue("name", "required", "이름은 필수입니다.");
        }
    };

    @Test void createStopsWhenValidateRejects() {
        Form form = new Form();
        BindingResult binding = new BeanPropertyBindingResult(form, "form");
        String view = controller.create(form, binding, new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("x/x/form");
        assertThat(binding.getFieldError("name").getDefaultMessage()).isEqualTo("이름은 필수입니다.");
        verify(service, never()).create(any());
    }

    @Test void updateSkipsIsNewOnlyRule() {
        Form form = new Form();
        BindingResult binding = new BeanPropertyBindingResult(form, "form");
        String view = controller.update(9L, form, binding, new ExtendedModelMap(), new RedirectAttributesModelMap());
        assertThat(view).isEqualTo("redirect:/x/9");
        verify(service).update(any(), any());
    }
}
