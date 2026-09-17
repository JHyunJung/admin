package com.crosscert.fidoadmin.fido2.web;

import com.crosscert.fidoadmin.fido2.entity.Fido2DemoAccessCode;
import java.time.LocalDateTime;

/** 목록·상세 공용 출력. epoch 원값과 서울 일시를 함께 싣는다. */
public record Fido2DemoAccessCodeView(String accesscode, String vendorname, Long starttime, Long endtime,
                                      LocalDateTime startAt, LocalDateTime endAt, String status, String note, String etc) {
    public static Fido2DemoAccessCodeView of(Fido2DemoAccessCode c) {
        return new Fido2DemoAccessCodeView(c.getAccesscode(), c.getVendorname(), c.getStarttime(), c.getEndtime(),
            EpochSeconds.fromEpoch(c.getStarttime()), EpochSeconds.fromEpoch(c.getEndtime()),
            c.getStatus(), c.getNote(), c.getEtc());
    }
}
