package com.crosscert.fidoadmin.auth;

import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * 비밀번호 만료. LAST_PW_CHANGE_DATE 로부터 90일이 지나면 로그인 뒤 변경 화면으로 보낸다.
 *
 * <p>열이 없는 DB 에서는 스스로 꺼진다({@code fido-admin.password-expiry.enabled=auto}, 기본). 끄면 이 열에
 * 전혀 접근하지 않는다. {@code false} 로 두면 탐지 쿼리도 돌리지 않는다.
 *
 * <p>열 탐지는 처음 필요할 때 한 번 하고 결과를 기억한다(기동 시에 하지 않는다 — DB 가 잠깐 흔들려도 기동이
 * 실패하거나 기능이 영영 꺼지지 않도록). 탐지가 일시 장애로 실패하면 이번 요청만 끈 것으로 보고 다음에 다시 시도한다.
 */
@Slf4j
@Component
public class PasswordExpiryPolicy {

    static final String PROP_EXPIRY_DAYS = "PW_EXPIRY_DAYS";
    static final int DEFAULT_DAYS = 90;

    private final PasswordAgeStore store;
    private final CcfaSystemPropRepository props;
    private final Clock clock;
    private final boolean disabledByConfig;
    private volatile Boolean probed;

    @Autowired
    public PasswordExpiryPolicy(PasswordAgeStore store, CcfaSystemPropRepository props,
                                @Value("${fido-admin.password-expiry.enabled:auto}") String mode) {
        this(store, props, mode, Clock.systemDefaultZone());
    }

    PasswordExpiryPolicy(PasswordAgeStore store, CcfaSystemPropRepository props, String mode, Clock clock) {
        this.store = store;
        this.props = props;
        this.clock = clock;
        this.disabledByConfig = "false".equalsIgnoreCase(mode == null ? "" : mode.trim());
        if (disabledByConfig) {
            log.info("비밀번호 만료를 설정으로 껐습니다(fido-admin.password-expiry.enabled=false).");
        }
    }

    public boolean enabled() {
        if (disabledByConfig) return false;
        Boolean cached = probed;
        if (cached != null) return cached;
        try {
            boolean exists = store.columnExists();
            probed = exists;
            if (!exists) log.warn("LAST_PW_CHANGE_DATE 열이 없어 비밀번호 만료를 끕니다.");
            return exists;
        } catch (DataAccessException e) {
            log.warn("비밀번호 만료 탐지 실패 — 이번 요청은 끈 것으로 봅니다", e);
            return false;
        }
    }

    public int expiryDays() {
        return props.findById(new CcfaSystemPropId(PROP_EXPIRY_DAYS, 0L))
            .map(p -> {
                try {
                    int v = Integer.parseInt(p.getPropValue().trim());
                    return v > 0 ? v : DEFAULT_DAYS;
                } catch (RuntimeException e) {
                    return DEFAULT_DAYS;
                }
            })
            .orElse(DEFAULT_DAYS);
    }

    /**
     * 로그인 직후 판정. 날짜가 없으면(열 추가 전 계정) 만료가 아니고 지금으로 기록한다 —
     * 배포 직후 모두를 한꺼번에 변경 화면으로 보내지 않고, 이 로그인부터 기간을 센다.
     */
    public boolean isExpiredOnLogin(String userId) {
        if (!enabled()) return false;
        Optional<LocalDateTime> last = store.lastChanged(userId);
        if (last.isEmpty()) {
            store.touch(userId);
            return false;
        }
        return !last.get().plusDays(expiryDays()).isAfter(LocalDateTime.now(clock));
    }

    /** 비밀번호가 바뀐 시각을 지금으로. 꺼져 있으면 아무것도 하지 않는다. */
    public void touch(String userId) {
        if (enabled()) store.touch(userId);
    }
}
