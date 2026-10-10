package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** V2 디자인 토큰과 번들 폰트 선언을 고정한다. 값은 캔버스 V2_DesignSystem 보드와 1:1 이다. */
class ThemeCssTest {

    private static String css() throws Exception {
        return Files.readString(Path.of("src/main/resources/static/css/admin.css"), StandardCharsets.UTF_8);
    }

    @Test void V2_토큰이_정의되어_있다() throws Exception {
        String css = css();
        assertThat(css).contains(
            "--fa-bg: #F6F8FB", "--fa-surface: #FFFFFF", "--fa-surface-2: #F1F4F9",
            "--fa-line: #E2E7EF", "--fa-line-strong: #C9D2DF",
            "--fa-ink: #0F1B2D", "--fa-ink-2: #3D4A5C", "--fa-ink-3: #5B6779",
            "--fa-primary: #1F5EDB", "--fa-primary-hover: #184BB0", "--fa-primary-soft: #E8EFFC", "--fa-primary-ink: #1846A3",
            "--fa-success: #157347", "--fa-success-soft: #E6F4EC",
            "--fa-warning: #9A5B00", "--fa-warning-soft: #FDF3E1",
            "--fa-danger: #B42318", "--fa-danger-soft: #FDECEA",
            "--fa-neutral: #3D4A5C", "--fa-neutral-soft: #EEF1F5");
    }

    @Test void 번들_폰트만_쓴다() throws Exception {
        String css = css();
        assertThat(css).contains("font-family: 'IBM Plex Sans KR'", "font-family: 'IBM Plex Mono'",
            "url(../fonts/IBMPlexSansKR-Regular.subset.woff2)", "url(../fonts/IBMPlexMono-Regular.subset.woff2)");
        assertThat(css).doesNotContain("Pretendard", "fonts.googleapis", "http://", "https://");
    }

    @Test void V1_남색_테마가_남아_있지_않다() throws Exception {
        assertThat(css()).doesNotContain("#1e2a44", "--fa-navy", "#3bafda", "#f3b84a");
    }
}
