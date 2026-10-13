package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** APPID 입력 폼. 길이 제한은 DB 컬럼 길이(바이트). 식별자(IDX)는 폼에 두지 않는다. */
@Getter @Setter
public class AppidForm {
    @NotBlank @ByteSize(max = 128) private String appid;
    @ByteSize(max = 512) private String memo;
    @NotBlank @ByteSize(max = 12) private String status = "use";
    @ByteSize(max = 128) private String device;
    @NotBlank @ByteSize(max = 1) private String deviceDefault = "F";
    @ByteSize(max = 512) private String servicename;
    // companyIdx 는 두지 않는다 — 폼에 입력 칸이 없고, CrudService 가 등록·수정 때 유효 테넌트로 강제한다.

    public static AppidForm from(Appid a) {
        AppidForm f = new AppidForm();
        f.appid = a.getAppid(); f.memo = a.getMemo(); f.status = a.getStatus(); f.device = a.getDevice();
        f.deviceDefault = a.getDeviceDefault(); f.servicename = a.getServicename();
        return f;
    }

    public void applyTo(Appid a) {
        a.setAppid(appid); a.setMemo(memo); a.setStatus(status); a.setDevice(device);
        a.setDeviceDefault(deviceDefault); a.setServicename(servicename);
    }
}
