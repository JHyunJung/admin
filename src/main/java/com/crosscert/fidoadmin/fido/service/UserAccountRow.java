package com.crosscert.fidoadmin.fido.service;

import java.time.LocalDateTime;

/**
 * 사용자 단위로 묶은 한 행. USERINFO 는 한 행이 한 기기(크리덴셜)라
 * {@code (USERID, SERVICENAME)} 로 묶어야 "사람" 단위가 된다.
 *
 * <p>묶음 키에 SERVICENAME 이 들어가는 이유: 같은 USERID 라도 서비스가 다르면
 * 다른 사람일 수 있다. USERID 만으로 묶으면 서로 다른 사용자의 기기가 한 행에 섞인다.
 *
 * @param deviceCount 등록된 기기 수
 * @param activeCount 그중 상태가 'O'(정상)인 기기 수
 * @param lastRegtime 가장 최근 등록 일시
 */
public record UserAccountRow(String userid, String servicename,
                             long deviceCount, long activeCount, LocalDateTime lastRegtime) {
}
