package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** CCFA_LICENSE 입력 폼. COMPANY_NAME 은 폼에 없다(서비스가 동기화). */
@Getter @Setter
public class LicenseForm {
    @NotNull(message = "고객사를 선택하세요.") private Long companyIdx;
    @ByteSize(max = 128) private String contactName;
    @ByteSize(max = 128) private String contactPhone;
    @ByteSize(max = 256) private String contactEmail;
    @ByteSize(max = 256) private String serviceName;
    @ByteSize(max = 1024) private String etc;
    @ByteSize(max = 2048) private String license;
    @ByteSize(max = 256) private String filePath;
    @ByteSize(max = 256) private String hashvalue;

    public static LicenseForm from(CcfaLicense l) {
        LicenseForm f = new LicenseForm();
        f.companyIdx = l.getCompanyIdx(); f.contactName = l.getContactName(); f.contactPhone = l.getContactPhone();
        f.contactEmail = l.getContactEmail(); f.serviceName = l.getServiceName(); f.etc = l.getEtc();
        f.license = l.getLicense(); f.filePath = l.getFilePath(); f.hashvalue = l.getHashvalue();
        return f;
    }

    public void applyTo(CcfaLicense l) {
        l.setCompanyIdx(companyIdx); l.setContactName(contactName); l.setContactPhone(contactPhone);
        l.setContactEmail(contactEmail); l.setServiceName(serviceName); l.setEtc(etc);
        l.setLicense(license); l.setFilePath(filePath); l.setHashvalue(hashvalue);
    }
}
