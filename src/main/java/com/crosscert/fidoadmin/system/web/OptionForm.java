package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaOption;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_OPTION(코드 그룹) 입력 폼. */
@Getter @Setter
public class OptionForm {
    @NotBlank @ByteSize(max = 64) private String optionName;
    @ByteSize(max = 64) private String optionNote;
    @ByteSize(max = 64) private String optionTitle;

    public static OptionForm from(CcfaOption o) {
        OptionForm f = new OptionForm();
        f.optionName = o.getOptionName(); f.optionNote = o.getOptionNote(); f.optionTitle = o.getOptionTitle();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaOption toNewEntity() {
        CcfaOption o = new CcfaOption();
        applyTo(o);
        return o;
    }

    public void applyTo(CcfaOption o) {
        o.setOptionName(optionName); o.setOptionNote(optionNote); o.setOptionTitle(optionTitle);
    }
}
