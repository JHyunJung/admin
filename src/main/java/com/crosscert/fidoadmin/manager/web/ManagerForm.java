package com.crosscert.fidoadmin.manager.web;

import com.crosscert.fidoadmin.common.ByteSize;
import com.crosscert.fidoadmin.manager.entity.CcfaManager;
import com.crosscert.fidoadmin.signup.SignupPolicy;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * CCFA_MANAGER 입력 폼. 비밀번호는 평문으로 받아 컨트롤러가 인코딩한다.
 * 등록 시 필수·수정 시 선택은 컨트롤러 validate() 가 본다(Bean Validation 으로는 구분할 수 없다).
 *
 * <p>companyIdx 는 Task 10 부터 폼 select 가 없다 — 세션이 정한 유효 테넌트를
 * {@code CrudService.create()}/{@code update()} 가 덮어쓴다. 그래서 여기 {@code @NotNull} 을
 * 두면 폼에 값이 실려 오지 않는 모든 요청이 {@code @Valid} 단계에서 막혀, 등록 자체가 불가능해진다.
 */
@Getter @Setter
public class ManagerForm {
    @NotBlank(message = "ID 는 필수입니다.") @ByteSize(max = 64) private String userId;
    @ByteSize(max = 128) private String password;
    private String passwordConfirm;
    @ByteSize(max = 50) private String userNm;
    @ByteSize(max = 256) private String userEmail;
    @ByteSize(max = 20) private String userPhone;
    private Long companyIdx;
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

    /**
     * 가입 신청 상태(승인대기·거절)인가. 화면이 상태 입력란을 읽기 전용으로 바꾸는 판단에 쓴다.
     * 실제 차단은 {@code ManagerService.update()} 가 한다(조작된 POST 는 폼을 거치지 않는다).
     */
    public boolean isSignupStatus() { return SignupPolicy.isSignupStatus(status); }
}
