package com.crosscert.fidoadmin.fido.service;

/** 붙여 넣은 메타데이터를 CRITERIA 로 옮길 수 없을 때. 메시지는 화면에 그대로 보인다. */
public class CriteriaMetadataException extends RuntimeException {
    public CriteriaMetadataException(String message) {
        super(message);
    }
}
