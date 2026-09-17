package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_MANAGER 입력 폼. 비밀번호는 평문으로 받아 컨트롤러가 인코딩한다.
 * 등록 시 필수·수정 시 선택은 컨트롤러 validate() 가 본다(Bean Validation 으로는 구분할 수 없다).
 */
@Getter @Setter
public class ManagerForm {
    @NotBlank(message = "ID 는 필수입니다.") @ByteSize(max = 64) private String userId;
    @ByteSize(max = 128) private String password;
    private String passwordConfirm;
    @ByteSize(max = 50) private String userNm;
    @ByteSize(max = 256) private String userEmail;
    @ByteSize(max = 20) private String userPhone;
    @NotNull(message = "고객사를 선택하세요.") private Long companyIdx;
    @NotBlank(message = "상태는 필수입니다.") @ByteSize(max = 20) private String status = "활성";
    @ByteSize(max = 2048) private String etc;
    @ByteSize(max = 32) private String alramType = "none";
    @ByteSize(max = 20) private String alramLevel = "0";

    /** USER_PW 는 절대 폼으로 옮기지 않는다. */
    public static ManagerForm from(CcfaManager m) {
        ManagerForm f = new ManagerForm();
        f.userId = m.getUserId(); f.userNm = m.getUserNm(); f.userEmail = m.getUserEmail(); f.userPhone = m.getUserPhone();
        f.companyIdx = m.getCompanyIdx(); f.status = m.getStatus(); f.etc = m.getEtc();
        f.alramType = m.getAlramType(); f.alramLevel = m.getAlramLevel();
        return f;
    }

    /** USER_ID 는 등록 시에만 채운다(수정 화면에서는 readonly, 로그인 키라 바꾸지 않는다). USER_PW 는 컨트롤러가 채운다. */
    public void applyTo(CcfaManager m) {
        if (m.getUserId() == null) m.setUserId(userId == null ? null : userId.trim());
        m.setUserNm(userNm); m.setUserEmail(userEmail); m.setUserPhone(userPhone);
        m.setCompanyIdx(companyIdx); m.setStatus(status); m.setEtc(etc);
        m.setAlramType(alramType); m.setAlramLevel(alramLevel);
    }

    public boolean hasPassword() { return password != null && !password.isBlank(); }
}
