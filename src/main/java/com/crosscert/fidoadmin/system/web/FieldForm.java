package com.crosscert.fidoadmin.system.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.system.entity.CcfaFields;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_FIELDS 입력 폼. PK/FK/EDITABLE 은 0/1 플래그를 NUMBER 로 저장한다. */
@Getter @Setter
public class FieldForm {
    @NotBlank @ByteSize(max = 128) private String fieldTable;
    @NotBlank @ByteSize(max = 256) private String fieldName;
    @NotBlank @ByteSize(max = 128) private String fieldType;
    @ByteSize(max = 128) private String fieldTitle;
    @NotNull @Min(0) private Long pk = 0L;
    @NotNull @Min(0) private Long fk = 0L;
    @NotNull @Min(0) private Long editable = 0L;
    /** 코드 그룹(CCFA_OPTION.IDX). 비우면 null. */
    private Long optionIdx;

    public static FieldForm from(CcfaFields e) {
        FieldForm f = new FieldForm();
        f.fieldTable = e.getFieldTable(); f.fieldName = e.getFieldName(); f.fieldType = e.getFieldType();
        f.fieldTitle = e.getFieldTitle(); f.pk = e.getPk(); f.fk = e.getFk(); f.editable = e.getEditable();
        f.optionIdx = e.getOptionIdx();
        return f;
    }

    /** 식별자(IDX)는 시퀀스가 채우므로 폼이 건드리지 않는다. */
    public CcfaFields toNewEntity() {
        CcfaFields e = new CcfaFields();
        applyTo(e);
        return e;
    }

    public void applyTo(CcfaFields e) {
        e.setFieldTable(fieldTable); e.setFieldName(fieldName); e.setFieldType(fieldType); e.setFieldTitle(fieldTitle);
        e.setPk(pk); e.setFk(fk); e.setEditable(editable); e.setOptionIdx(optionIdx);
    }
}
