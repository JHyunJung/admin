package com.crosscert.fidoadmin.system.service;

import com.crosscert.fidoadmin.audit.AuditLogger;
import com.crosscert.fidoadmin.audit.AuditType;
import com.crosscert.fidoadmin.common.Specs;
import com.crosscert.fidoadmin.common.TenantContext;
import com.crosscert.fidoadmin.system.entity.CcfaSystemProp;
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId;
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crosscert.fidoadmin.system.web.FidoSettingKey;

/**
 * FIDO 서버 설정 화면의 읽기·저장. 값은 {@code CCFA_SYSTEM_PROP} 의
 * {@code (PROP_KEY, 유효 테넌트)} 행 하나씩에 들어간다.
 *
 * <p>{@code CrudService} 를 상속하지 않는다. 그쪽은 "행 하나를 CRUD 하는 화면"을 위한
 * 기반이고, 여기는 <b>정해진 22개 키를 한 번에 읽고 한 번에 저장하는</b> 화면이라
 * 목록·상세·삭제 개념이 없다. 대신 테넌트 경계는 같은 규칙을 직접 지킨다 —
 * 읽기는 유효 테넌트로 필터하고, 저장은 유효 테넌트로 식별자를 만든다.
 *
 * <p>키 이름이 아직 운영 서버와 맞춰지지 않았다는 점은 {@link FidoSettingKey} 참고.
 */
@Service
@RequiredArgsConstructor
public class FidoSettingService {

    private final CcfaSystemPropRepository repository;
    private final AuditLogger audit;
    private final TenantContext tenant;

    /**
     * 화면에 채울 값. 저장된 행이 없는 키는 {@link FidoSettingKey#defaultValue()} 를 쓴다.
     * 항상 22개 키를 모두 돌려주므로 템플릿이 null 을 만나지 않는다.
     */
    @Transactional(readOnly = true)
    public Map<String, String> load() {
        Map<String, String> stored = new TreeMap<>();
        for (CcfaSystemProp p : repository.findAll(Specs.eq("id.companyIdx", tenant.companyIdx()))) {
            if (p.getId() != null) stored.put(p.getId().getPropKey(), p.getPropValue());
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (FidoSettingKey k : FidoSettingKey.values()) {
            String v = stored.get(k.key());
            values.put(k.key(), v == null ? k.defaultValue() : v);
        }
        return values;
    }

    /**
     * 받은 값을 저장한다. 행이 있으면 값만 갱신하고 없으면 만든다.
     *
     * <p>{@link FidoSettingKey} 에 없는 키는 <b>무시한다</b>. 조작된 POST 로 임의의
     * PROP_KEY 를 만들지 못하게 하려는 것이다(그 용도는 {@code /system/props} 화면에 있다).
     */
    @Transactional
    public void save(Map<String, String> submitted) {
        Long company = tenant.companyIdx();
        int changed = 0;
        for (FidoSettingKey k : FidoSettingKey.values()) {
            String value = submitted.get(k.key());
            if (value == null) continue;
            CcfaSystemPropId id = new CcfaSystemPropId(k.key(), company);
            CcfaSystemProp row = repository.findById(id).orElseGet(() -> {
                CcfaSystemProp fresh = new CcfaSystemProp();
                fresh.setId(id);
                fresh.setShareType("NO");
                return fresh;
            });
            row.setPropValue(value);
            row.setUpdatedtime(LocalDateTime.now());
            repository.save(row);
            changed++;
        }
        if (changed > 0) {
            audit.log(AuditType.UPDATE, "CCFA_SYSTEM_PROP FIDO SETTINGS " + changed + "건");
        }
    }
}
