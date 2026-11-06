package com.crosscert.fidoadmin.auth

import com.crosscert.fidoadmin.common.javaTrim
import com.crosscert.fidoadmin.system.entity.CcfaSystemPropId
import com.crosscert.fidoadmin.system.repository.CcfaSystemPropRepository
import java.time.Clock
import java.time.LocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component

/**
 * 비밀번호 만료. LAST_PW_CHANGE_DATE 로부터 90일이 지나면 로그인 뒤 변경 화면으로 보낸다.
 *
 * 열이 없는 DB 에서는 스스로 꺼진다(`fido-admin.password-expiry.enabled=auto`, 기본). 끄면 이 열에
 * 전혀 접근하지 않는다. `false` 로 두면 탐지 쿼리도 돌리지 않는다.
 *
 * 열 탐지는 처음 필요할 때 한 번 하고 결과를 기억한다(기동 시에 하지 않는다 — DB 가 잠깐 흔들려도 기동이
 * 실패하거나 기능이 영영 꺼지지 않도록). 탐지가 일시 장애로 실패하면 이번 요청만 끈 것으로 보고 다음에 다시 시도한다.
 */
@Component
class PasswordExpiryPolicy internal constructor(
    private val store: PasswordAgeStore,
    private val props: CcfaSystemPropRepository,
    mode: String?,
    private val clock: Clock,
) {

    @Autowired
    constructor(
        store: PasswordAgeStore,
        props: CcfaSystemPropRepository,
        @Value("\${fido-admin.password-expiry.enabled:auto}") mode: String?,
    ) : this(store, props, mode, Clock.systemDefaultZone())

    private val disabledByConfig: Boolean = "false".equals(mode?.javaTrim() ?: "", ignoreCase = true)

    @Volatile
    private var probed: Boolean? = null

    init {
        if (disabledByConfig) {
            log.info("비밀번호 만료를 설정으로 껐습니다(fido-admin.password-expiry.enabled=false).")
        }
    }

    fun enabled(): Boolean {
        if (disabledByConfig) return false
        val cached = probed
        if (cached != null) return cached
        try {
            val exists = store.columnExists()
            probed = exists
            if (!exists) log.warn("LAST_PW_CHANGE_DATE 열이 없어 비밀번호 만료를 끕니다.")
            return exists
        } catch (e: DataAccessException) {
            log.warn("비밀번호 만료 탐지 실패 — 이번 요청은 끈 것으로 봅니다", e)
            return false
        }
    }

    fun expiryDays(): Int =
        props.findById(CcfaSystemPropId(PROP_EXPIRY_DAYS, 0L))
            .map { p ->
                try {
                    val v = Integer.parseInt(p.propValue!!.javaTrim())
                    if (v > 0) v else DEFAULT_DAYS
                } catch (e: RuntimeException) {
                    DEFAULT_DAYS
                }
            }
            .orElse(DEFAULT_DAYS)

    /**
     * 로그인 직후 판정. 날짜가 없으면(열 추가 전 계정) 만료가 아니고 지금으로 기록한다 —
     * 배포 직후 모두를 한꺼번에 변경 화면으로 보내지 않고, 이 로그인부터 기간을 센다.
     */
    fun isExpiredOnLogin(userId: String?): Boolean {
        if (!enabled()) return false
        val last = store.lastChanged(userId)
        if (last.isEmpty) {
            store.touch(userId)
            return false
        }
        return !last.get().plusDays(expiryDays().toLong()).isAfter(LocalDateTime.now(clock))
    }

    /** 비밀번호가 바뀐 시각을 지금으로. 꺼져 있으면 아무것도 하지 않는다. */
    fun touch(userId: String?) {
        if (enabled()) store.touch(userId)
    }

    companion object {
        private val log = LoggerFactory.getLogger(PasswordExpiryPolicy::class.java)

        internal const val PROP_EXPIRY_DAYS = "PW_EXPIRY_DAYS"
        internal const val DEFAULT_DAYS = 90
    }
}
