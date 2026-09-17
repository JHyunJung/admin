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
