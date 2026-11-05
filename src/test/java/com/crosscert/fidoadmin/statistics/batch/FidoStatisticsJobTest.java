package com.crosscert.fidoadmin.statistics.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FidoStatisticsJobTest {

    private final HourlyAggregator aggregator = mock(HourlyAggregator.class);
    private final StatisticsWriter writer = mock(StatisticsWriter.class);

    private FidoStatisticsJob job(int catchUpHours) {
        return new FidoStatisticsJob(new StatisticsBatchProperties(true, "0 15 * * * *", catchUpHours), aggregator, writer);
    }

    private static Map<StatKey, StatCounts> one() {
        return Map.of(new StatKey(1L, "svc"), new StatCounts().add(LogClassifier.Kind.AUTH, true));
    }

    /** 2026-11-05 10:15 실행 → 가장 늦은 대상은 24시간 전 정각(11/04 10시), 3시간이면 08~10시. */
    @Test void 대상은_24시간_전_정각까지다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of());
        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15));

        verify(writer).loadedGroupbys(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/10"));
        verify(aggregator).aggregate(LocalDate.of(2026, 11, 4),
            List.of(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/09"), StatHour.parse("2026/11/04/10")));
    }

    @Test void 이미_있는_시간은_건너뛴다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of("2026/11/04/09"));
        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15));

        verify(aggregator).aggregate(LocalDate.of(2026, 11, 4),
            List.of(StatHour.parse("2026/11/04/08"), StatHour.parse("2026/11/04/10")));
    }

    @Test void 모두_있으면_아무것도_읽지_않는다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of("2026/11/04/10"));
        job(1).run(LocalDateTime.of(2026, 11, 5, 10, 15));
        verify(aggregator, never()).aggregate(any(), any());
    }

    /** 자정을 걸치면 날짜 테이블마다 한 번씩, 오래된 날짜부터 읽는다. */
    @SuppressWarnings("unchecked")
    @Test void 날짜별로_한번씩_오래된_날짜부터_읽는다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of());
        job(3).run(LocalDateTime.of(2026, 11, 5, 1, 15)); // 대상 11/03 23시, 11/04 00·01시

        ArgumentCaptor<LocalDate> dates = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<Collection<StatHour>> hours = ArgumentCaptor.forClass(Collection.class);
        verify(aggregator, org.mockito.Mockito.times(2)).aggregate(dates.capture(), hours.capture());
        assertThat(dates.getAllValues()).containsExactly(LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 4));
        assertThat(hours.getAllValues().get(0)).containsExactly(StatHour.parse("2026/11/03/23"));
        assertThat(hours.getAllValues().get(1)).containsExactly(StatHour.parse("2026/11/04/00"), StatHour.parse("2026/11/04/01"));
    }

    @Test void 로그가_없는_시간은_쓰지_않는다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of());
        StatHour h9 = StatHour.parse("2026/11/04/09");
        when(aggregator.aggregate(any(), any())).thenReturn(Map.of(h9, one()));

        job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15));

        verify(writer).write(eq(h9), anyMap());
        verify(writer, org.mockito.Mockito.times(1)).write(any(), anyMap());
    }

    @Test void 한_시간이_실패해도_다음_시간은_계속한다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of());
        StatHour h8 = StatHour.parse("2026/11/04/08");
        StatHour h9 = StatHour.parse("2026/11/04/09");
        when(aggregator.aggregate(any(), any())).thenReturn(Map.of(h8, one(), h9, one()));
        when(writer.write(eq(h8), anyMap())).thenThrow(new RuntimeException("boom"));
        when(writer.write(eq(h9), anyMap())).thenReturn(1);

        assertThat(job(3).run(LocalDateTime.of(2026, 11, 5, 10, 15))).isEqualTo(1);
        verify(writer).write(eq(h9), anyMap());
    }

    @Test void 한_날짜를_읽지_못해도_다음_날짜는_계속한다() {
        when(writer.loadedGroupbys(any(), any())).thenReturn(Set.of());
        StatHour h0 = StatHour.parse("2026/11/04/00");
        when(aggregator.aggregate(eq(LocalDate.of(2026, 11, 3)), any())).thenThrow(new RuntimeException("ORA-xxx"));
        when(aggregator.aggregate(eq(LocalDate.of(2026, 11, 4)), any())).thenReturn(Map.of(h0, one()));
        when(writer.write(eq(h0), anyMap())).thenReturn(1);

        assertThat(job(2).run(LocalDateTime.of(2026, 11, 5, 0, 15))).isEqualTo(1);
    }

    @Test void 이전_실행이_돌고_있으면_건너뛴다() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        when(writer.loadedGroupbys(any(), any())).thenAnswer(inv -> {
            calls.incrementAndGet();
            inside.countDown();
            release.await(5, TimeUnit.SECONDS);
            return Set.of("2026/11/04/10");
        });
        FidoStatisticsJob job = job(1);
        Thread first = new Thread(() -> job.run(LocalDateTime.of(2026, 11, 5, 10, 15)));
        first.start();
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(job.run(LocalDateTime.of(2026, 11, 5, 10, 15))).isEqualTo(-1);

        release.countDown();
        first.join(5000);
        assertThat(calls.get()).isEqualTo(1);
        assertThat(job.run(LocalDateTime.of(2026, 11, 5, 10, 15))).isZero(); // 끝난 뒤에는 다시 돈다
    }
}
