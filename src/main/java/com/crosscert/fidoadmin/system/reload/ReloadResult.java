package com.crosscert.fidoadmin.system.reload;

/** FIDO 서버 한 대에 reload 를 보낸 결과. detail 은 성공이면 "HTTP 200", 실패면 상태나 예외 한 줄(200자 이내). */
public record ReloadResult(String servercode, String serverurl, boolean ok, String detail, long elapsedMs) {}
