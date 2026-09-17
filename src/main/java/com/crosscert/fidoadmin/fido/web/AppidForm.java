package com.crosscert.fidoadmin.fido.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido.entity.Appid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** APPID 입력 폼. 길이 제한은 ERD 값(바이트). 식별자(IDX)는 폼에 두지 않는다. */
@Getter @Setter
public class AppidForm {
    @NotBlank @ByteSize(max = 128) private String appid;
    @ByteSize(max = 512) private String memo;
    @NotBlank @ByteSize(max = 12) private String status = "use";
    @ByteSize(max = 128) private String device;
    @NotBlank @ByteSize(max = 1) private String deviceDefault = "F";
    @ByteSize(max = 512) private String servicename;
    /** SUPER 만 선택. COMPANY 는 서비스가 자기 고객사로 강제한다. */
    private Long companyIdx;

    public static AppidForm from(Appid a) {
        AppidForm f = new AppidForm();
        f.appid = a.getAppid(); f.memo = a.getMemo(); f.status = a.getStatus(); f.device = a.getDevice();
        f.deviceDefault = a.getDeviceDefault(); f.servicename = a.getServicename(); f.companyIdx = a.getCompanyIdx();
        return f;
    }

    public void applyTo(Appid a) {
        a.setAppid(appid); a.setMemo(memo); a.setStatus(status); a.setDevice(device);
        a.setDeviceDefault(deviceDefault); a.setServicename(servicename); a.setCompanyIdx(companyIdx);
    }
}
