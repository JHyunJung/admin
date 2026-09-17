package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaMenu;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_MENU 입력 폼. 길이 제한은 ERD 값. */
@Getter @Setter
public class MenuForm {
    @NotBlank @ByteSize(max = 32) private String menuName;
    @ByteSize(max = 32) private String menuCode;
    @NotNull private Long menuParentIdx = 0L;
    @ByteSize(max = 64) private String menuIcon;
    @ByteSize(max = 512) private String menuUrl;
    private Long menuSeq;
    @ByteSize(max = 128) private String tblName;
    @ByteSize(max = 128) private String pk;
    @NotBlank @ByteSize(max = 20) private String visible = "true";
    @NotBlank @ByteSize(max = 20) private String openType = "open";
    @NotBlank @ByteSize(max = 20) private String statistics = "N";
    @NotBlank @ByteSize(max = 20) private String readonly = "N";

    public static MenuForm from(CcfaMenu m) {
        MenuForm f = new MenuForm();
        f.menuName = m.getMenuName(); f.menuCode = m.getMenuCode(); f.menuParentIdx = m.getMenuParentIdx();
        f.menuIcon = m.getMenuIcon(); f.menuUrl = m.getMenuUrl(); f.menuSeq = m.getMenuSeq();
        f.tblName = m.getTblName(); f.pk = m.getPk(); f.visible = m.getVisible(); f.openType = m.getOpenType();
        f.statistics = m.getStatistics(); f.readonly = m.getReadonly();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaMenu toNewEntity() {
        CcfaMenu m = new CcfaMenu();
        applyTo(m);
        return m;
    }

    public void applyTo(CcfaMenu m) {
        m.setMenuName(menuName); m.setMenuCode(menuCode); m.setMenuParentIdx(menuParentIdx);
        m.setMenuIcon(menuIcon); m.setMenuUrl(menuUrl); m.setMenuSeq(menuSeq);
        m.setTblName(tblName); m.setPk(pk); m.setVisible(visible); m.setOpenType(openType);
        m.setStatistics(statistics); m.setReadonly(readonly);
    }
}
