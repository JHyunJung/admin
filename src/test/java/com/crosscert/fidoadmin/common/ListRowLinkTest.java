package com.crosscert.fidoadmin.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * 목록 표의 "행 클릭 → 상세" 배선을 지킨다.
 *
 * <p>행 클릭은 admin.js 가 <em>행의 첫 번째 링크</em>를 그 행이 가리키는 곳으로 읽어 동작한다.
 * 그래서 목록 템플릿이 "행당 상세 링크 하나" 라는 전제를 지켜야 한다. 행에 링크가 둘이 되면
 * 사용자가 행을 눌렀을 때 의도하지 않은 쪽으로 가고, 하나도 없으면 행이 죽는다.
 *
 * <p>브라우저 동작 자체는 서버 테스트로 잡을 수 없다. 대신 그 동작이 딛고 선 전제를 고정한다.
 * (실제 클릭·새 탭·이중 이동 여부는 브라우저로 확인했다.)
 */
class ListRowLinkTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** tbody 안의 데이터 행. 빈 목록 행(th:if 로 그리는 "데이터가 없습니다")은 제외한다. */
    private static final Pattern TBODY = Pattern.compile("<tbody>(.*?)</tbody>", Pattern.DOTALL);
    private static final Pattern ROW = Pattern.compile("<tr th:each=\"[^\"]*\">(.*?)</tr>", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("<a\\s[^>]*th:href", Pattern.DOTALL);

    private static List<Path> listTemplates() throws IOException {
        try (Stream<Path> paths = Files.walk(TEMPLATES)) {
            return paths.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".html"))
                .filter(ListRowLinkTest::hasDataRows)
                .sorted()
                .toList();
        }
    }

    private static boolean hasDataRows(Path p) {
        String html = read(p);
        Matcher tbody = TBODY.matcher(html);
        while (tbody.find()) {
            if (ROW.matcher(tbody.group(1)).find()) return true;
        }
        return false;
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(p + " 읽기 실패", e);
        }
    }

    /**
     * 데이터 행의 링크는 둘 이상이면 안 된다. admin.js 가 <em>첫</em> 링크로 가므로,
     * 둘이 되는 순간 사용자가 행을 눌렀을 때 어디로 갈지가 템플릿의 작성 순서에 좌우된다.
     *
     * <p>링크가 0개인 행은 허용한다. 상세 화면이 없고 행 안의 버튼으로만 조작하는 목록이
     * 실제로 있다(/system/options 상세 안의 코드 목록 — 삭제 버튼만 있다). 그런 행은
     * admin.js 가 갈 곳을 못 찾아 그냥 지나가고 커서도 바뀌지 않는다.
     */
    @Test void 목록의_데이터_행에는_상세_링크가_둘_이상_있지_않다() throws IOException {
        List<String> offenders = new ArrayList<>();

        for (Path p : listTemplates()) {
            String rel = TEMPLATES.relativize(p).toString();
            Matcher tbody = TBODY.matcher(read(p));
            while (tbody.find()) {
                Matcher row = ROW.matcher(tbody.group(1));
                while (row.find()) {
                    long links = LINK.matcher(row.group(1)).results().count();
                    if (links > 1) offenders.add(rel + " (링크 " + links + "개)");
                }
            }
        }

        assertThat(offenders)
            .as("행 클릭은 행의 첫 링크로 간다. 링크가 둘이면 어디로 갈지가 작성 순서에 좌우된다")
            .isEmpty();
    }

    /** 실제 목록 화면들은 행당 링크가 하나다. 이 전제가 깨지면 행 클릭이 조용히 죽는다. */
    @Test void 주요_목록_화면은_행당_링크가_하나다() throws IOException {
        List<String> linkless = new ArrayList<>();

        for (Path p : listTemplates()) {
            if (!p.getFileName().toString().equals("list.html")) continue;
            String rel = TEMPLATES.relativize(p).toString();
            Matcher tbody = TBODY.matcher(read(p));
            while (tbody.find()) {
                Matcher row = ROW.matcher(tbody.group(1));
                while (row.find()) {
                    if (LINK.matcher(row.group(1)).results().count() == 0) linkless.add(rel);
                }
            }
        }

        // signup/list.html 은 표가 아니라 카드라 여기 걸리지 않는다(상세 화면 자체가 없다).
        assertThat(linkless).as("목록 화면의 행에는 상세로 가는 링크가 있어야 한다").isEmpty();
    }

    /** 배선이 통째로 사라지는 회귀를 막는다(목록 화면이 있는 한 대상은 0이 될 수 없다). */
    @Test void 행_클릭_대상_목록이_존재한다() throws IOException {
        assertThat(listTemplates()).hasSizeGreaterThanOrEqualTo(20);
    }

    /** admin.js 와 CSS 가 같은 표식을 쓰는지 고정한다 — 한쪽만 바뀌면 커서나 이동이 조용히 죽는다. */
    @Test void 행_클릭_배선이_스크립트와_스타일에_모두_있다() {
        String js = read(Path.of("src/main/resources/static/js/admin.js"));
        String css = read(Path.of("src/main/resources/static/css/admin.css"));

        assertThat(js).contains(".fa-table tbody tr");
        assertThat(js).contains("fa-row-link");
        assertThat(css).contains("fa-row-link");
    }
}
