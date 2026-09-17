package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * FIDO2_DEMO_ACCESS_CODE 입력 폼. 시작·종료는 화면에서 일시로 받고 저장 시 epoch 초로 바꾼다.
 * ACCESSCODE 는 할당형 PK 라 수정 화면에서는 readonly 이고 applyTo 는 기존 값을 바꾸지 않는다.
 */
@Getter @Setter
public class Fido2DemoAccessCodeForm {
    @NotBlank @ByteSize(max = 128) private String accesscode;
    @ByteSize(max = 128) private String vendorname;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime starttime;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime endtime;
    @NotBlank @ByteSize(max = 1) private String status = "E";
    @ByteSize(max = 300) private String note;
    @ByteSize(max = 1024) private String etc;

    public static Fido2DemoAccessCodeForm from(Fido2DemoAccessCode c) {
        Fido2DemoAccessCodeForm f = new Fido2DemoAccessCodeForm();
        f.accesscode = c.getAccesscode(); f.vendorname = c.getVendorname();
        f.starttime = EpochSeconds.fromEpoch(c.getStarttime()); f.endtime = EpochSeconds.fromEpoch(c.getEndtime());
        f.status = c.getStatus(); f.note = c.getNote(); f.etc = c.getEtc();
        return f;
    }

    /** 신규 엔티티에만 식별자를 채운다. 수정 시에는 applyTo 만 쓰므로 식별자가 바뀌지 않는다. */
    public Fido2DemoAccessCode toNewEntity() {
        Fido2DemoAccessCode c = new Fido2DemoAccessCode();
        c.setAccesscode(accesscode == null ? null : accesscode.trim());
        applyTo(c);
        return c;
    }

    public void applyTo(Fido2DemoAccessCode c) {
        c.setVendorname(vendorname);
        c.setStarttime(EpochSeconds.toEpoch(starttime)); c.setEndtime(EpochSeconds.toEpoch(endtime));
        c.setStatus(status); c.setNote(note); c.setEtc(etc);
    }
}
