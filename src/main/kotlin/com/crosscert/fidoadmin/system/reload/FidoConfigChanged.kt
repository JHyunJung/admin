package com.crosscert.fidoadmin.system.reload

/**
 * FIDO 서버가 다시 읽어야 하는 데이터가 바뀌었다. 커밋 뒤 [FidoReloadListener] 가 reload 를 보낸다.
 * reason 은 로그용 한 줄(예: "APPID 수정 12").
 *
 * Java record 와 같은 JVM 모양(접근자 `reason()`)을 유지하려고 `@JvmRecord` 로 둔다.
 */
@JvmRecord
data class FidoConfigChanged(val reason: String?)
