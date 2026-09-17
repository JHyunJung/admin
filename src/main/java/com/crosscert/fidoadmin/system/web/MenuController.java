package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.CrudController;
import com.crosscert.fidoadmin.common.CrudService;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import com.crosscert.fidoadmin.system.service.MenuService;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/system/menus")
@RequiredArgsConstructor
public class MenuController extends CrudController<CcfaMenu, Long, MenuForm, MenuSearchForm> {

    private final MenuService service;

    @Override protected CrudService<CcfaMenu, Long, MenuSearchForm> service() { return service; }
    @Override protected String basePath() { return "/system/menus"; }
    @Override protected String viewDir() { return "system/menus"; }
    @Override protected MenuSearchForm newSearchForm() { return new MenuSearchForm(); }
    @Override protected MenuForm newForm() { return new MenuForm(); }
    @Override protected MenuForm toForm(CcfaMenu e) { return MenuForm.from(e); }
    @Override protected CcfaMenu toEntity(MenuForm f) { return f.toNewEntity(); }

    /**
     * 등록·수정 공통: 부모로 지정한 IDX 가 0(최상위)이거나 실제 존재하는 메뉴여야 한다.
     * 이 검증은 applyForm(엔티티 반영) 이전에 돌기 때문에, 존재하지 않는(허공의) 부모는
     * DB 에 쓰이기 전에 걸러진다. 자기 자신·하위 순환은 수정 시 applyForm 에서 별도로 막는다.
     */
    @Override protected void validate(MenuForm form, boolean isNew, BindingResult binding) {
        if (form.getMenuParentIdx() != null && !service.parentExists(form.getMenuParentIdx())) {
            binding.rejectValue("menuParentIdx", "unknown", "존재하지 않는 부모 메뉴입니다.");
        }
    }

    /**
     * 폼의 select 는 자기 자신과 하위 메뉴를 빼고 그리지만(템플릿 + populateFormModel 이중 방어),
     * 요청을 조작해 자기 자신이나 하위 메뉴를 부모로 보내면 트리가 순환한다. 여기서 막으면
     * CrudController.update() 가 DataIntegrityViolationException 을 잡아 폼을 다시 렌더링한다.
     */
    @Override protected void applyForm(MenuForm f, CcfaMenu e) {
        if (service.isSelfOrDescendant(f.getMenuParentIdx(), e.getIdx())) {
            throw new DataIntegrityViolationException("자기 자신이나 하위 메뉴를 부모로 지정할 수 없습니다.");
        }
        f.applyTo(e);
    }

    @Override protected void populateFormModel(Model model) {
        Long editingIdx = (Long) model.getAttribute("id");
        model.addAttribute("menus", service.selectableParentsFor(editingIdx));
    }
    @Override protected void populateListModel(Model model) { model.addAttribute("parentNames", parentNames()); }
    @Override protected void populateDetailModel(CcfaMenu e, Model model) { model.addAttribute("parentNames", parentNames()); }

    private Map<Long, String> parentNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        names.put(0L, "최상위");
        for (CcfaMenu m : service.allForSelect()) names.put(m.getIdx(), m.getMenuName());
        return names;
    }
}
