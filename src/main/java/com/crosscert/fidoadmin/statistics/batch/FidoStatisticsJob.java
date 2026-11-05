package com.crosscert.fidoadmin.statistics.batch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * FIDO_LOGS_yyyyMMdd → FIDO_STATISTICS 시간별 집계. 구 어드민의 FIDOLogScheduler 를 대신한다.
 *
 * <p>매 실행마다 최근 {@code catch-up-hours} 안에서 GROUPBY 가 없는 시간을 오래된 것부터 모두 처리한다.
 * 가장 늦은 대상은 {@code 정각(now) - 24h} 이다(구 배치와 같은 지연 — 그 날짜 테이블이 다 찬 뒤에 센다).
 * 로그가 없는 시간은 행이 생기지 않으므로 다음 실행에 다시 확인한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "fido-admin.statistics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FidoStatisticsJob {

    static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    static final int DELAY_HOURS = 24;

    private final StatisticsBatchProperties props;
    private final HourlyAggregator aggregator;
    private final StatisticsWriter writer;
    private final AtomicBoolean running = new AtomicBoolean();

    @Scheduled(cron = "${fido-admin.statistics.cron:0 15 * * * *}", zone = "Asia/Seoul")
    public void run() {
        run(LocalDateTime.now(ZONE));
    }

    /** @return 이번 실행에서 넣은 행 수. 이전 실행이 아직 돌고 있으면 -1 */
    int run(LocalDateTime now) {
        if (!running.compareAndSet(false, true)) {
            log.warn("statistics: 이전 실행이 아직 끝나지 않아 건너뜁니다");
            return -1;
        }
        try {
            return process(now);
        } finally {
            running.set(false);
        }
    }

    private int process(LocalDateTime now) {
        StatHour latest = StatHour.of(now).plusHours(-DELAY_HOURS);
        StatHour oldest = latest.plusHours(-(props.catchUpHours() - 1L));
        Set<String> done = writer.loadedGroupbys(oldest, latest);

        Map<LocalDate, List<StatHour>> missing = new TreeMap<>();
        for (StatHour h = oldest; h.compareTo(latest) <= 0; h = h.plusHours(1)) {
            if (!done.contains(h.groupby())) missing.computeIfAbsent(h.date(), d -> new ArrayList<>()).add(h);
        }
        if (missing.isEmpty()) return 0;

        int total = 0;
        for (Map.Entry<LocalDate, List<StatHour>> e : missing.entrySet()) {
            Map<StatHour, Map<StatKey, StatCounts>> byHour;
            try {
                byHour = aggregator.aggregate(e.getKey(), e.getValue());
            } catch (RuntimeException ex) {
                log.error("statistics: {} 로그 집계 실패", e.getKey(), ex);
                continue;
            }
            for (StatHour h : e.getValue()) {
                Map<StatKey, StatCounts> counts = byHour.get(h);
                if (counts == null || counts.isEmpty()) continue;
                try {
                    total += writer.write(h, counts);
                } catch (RuntimeException ex) {
                    log.error("statistics: {} 적재 실패", h.groupby(), ex);
                }
            }
        }
        return total;
    }
}
