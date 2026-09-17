package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaOptions;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** CCFA_OPTIONS(코드) 인라인 추가 폼. OPTION_IDX 는 경로에서 오므로 폼에 없다. */
@Getter @Setter
public class OptionItemForm {
    @NotBlank @ByteSize(max = 128) private String optionValue;
    @ByteSize(max = 128) private String optionTitle;
    @ByteSize(max = 128) private String optionNote;

    public CcfaOptions toNewEntity() {
        CcfaOptions i = new CcfaOptions();
        i.setOptionValue(optionValue); i.setOptionTitle(optionTitle); i.setOptionNote(optionNote);
        return i;
    }
}
