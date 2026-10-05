package com.crosscert.fidoadmin.fido.web;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** AAID 정책 등록 폼. 메타데이터 문 JSON 원문 하나만 받는다. 형식 검증은 CriteriaMetadataParser 가 한다. */
@Getter @Setter
public class CriteriaMetadataForm {
    @NotBlank(message = "메타데이터 JSON 을 입력하세요.")
    private String jsondata;
}
