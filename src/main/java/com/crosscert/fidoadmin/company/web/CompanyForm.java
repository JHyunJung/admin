package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.company.entity.CcfaCompany;
import com.crosscert.fidoadmin.common.ByteSize;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** CCFA_COMPANY 입력 폼. 길이 제한은 ERD 값. */
@Getter @Setter
public class CompanyForm {
    @NotBlank @ByteSize(max = 256) private String companyName;
    @ByteSize(max = 20) private String companyType;
    @ByteSize(max = 20) private String vendorCode;
    @ByteSize(max = 512) private String contact;
    @ByteSize(max = 50) private String contactPhone;
    @ByteSize(max = 50) private String contactPhone2;
    @ByteSize(max = 2048) private String contactAddr;
    @NotBlank @ByteSize(max = 20) private String enableType = "Y";
    // HTML 의 <input type="datetime-local"> 값 형식은 "yyyy-MM-ddTHH:mm" (초·오프셋 없음)이라
    // ISO.DATE_TIME(오프셋 포함) 으로는 바인딩이 실패한다. 화면 입력 형식에 맞춘 패턴을 쓴다.
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime starttime;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm") private LocalDateTime endtime;
    @NotNull @Min(0) private Long maxAppid = 0L;
    @NotNull @Min(0) private Long maxAppserver = 0L;
    @NotNull @Min(0) private Long maxUser = 0L;
    @ByteSize(max = 4000) private String etc;

    public static CompanyForm from(CcfaCompany c) {
        CompanyForm f = new CompanyForm();
        f.companyName = c.getCompanyName(); f.companyType = c.getCompanyType(); f.vendorCode = c.getVendorCode();
        f.contact = c.getContact(); f.contactPhone = c.getContactPhone(); f.contactPhone2 = c.getContactPhone2();
        f.contactAddr = c.getContactAddr(); f.enableType = c.getEnableType(); f.starttime = c.getStarttime();
        f.endtime = c.getEndtime(); f.maxAppid = c.getMaxAppid(); f.maxAppserver = c.getMaxAppserver();
        f.maxUser = c.getMaxUser(); f.etc = c.getEtc();
        return f;
    }

    public void applyTo(CcfaCompany c) {
        c.setCompanyName(companyName); c.setCompanyType(companyType); c.setVendorCode(vendorCode);
        c.setContact(contact); c.setContactPhone(contactPhone); c.setContactPhone2(contactPhone2);
        c.setContactAddr(contactAddr); c.setEnableType(enableType); c.setStarttime(starttime);
        c.setEndtime(endtime); c.setMaxAppid(maxAppid); c.setMaxAppserver(maxAppserver);
        c.setMaxUser(maxUser); c.setEtc(etc);
    }
}
