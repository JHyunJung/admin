package com.crosscert.fidoadmin.company.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.company.entity.CcfaLicense;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_LICENSE 입력 폼. COMPANY_NAME 은 폼에 없다(서비스가 동기화).
 *
 * <p>companyIdx 는 Task 10 부터 폼 select 가 없다 — 세션이 정한 유효 테넌트를
 * {@code CrudService.create()}/{@code update()} 가 덮어쓴다. 그래서 여기 {@code @NotNull} 을
 * 두면 폼에 값이 실려 오지 않는 모든 요청이 {@code @Valid} 단계에서 막혀, 등록 자체가 불가능해진다.
 */
@Getter @Setter
public class LicenseForm {
    private Long companyIdx;
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
