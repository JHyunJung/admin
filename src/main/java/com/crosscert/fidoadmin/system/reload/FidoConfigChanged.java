package com.crosscert.fidoadmin.system.reload;

/**
 * FIDO 서버가 다시 읽어야 하는 데이터가 바뀌었다. 커밋 뒤 {@link FidoReloadListener} 가 reload 를 보낸다.
 * reason 은 로그용 한 줄(예: "APPID 수정 12").
 */
public record FidoConfigChanged(String reason) {}
