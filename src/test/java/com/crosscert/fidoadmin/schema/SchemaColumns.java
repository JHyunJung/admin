package com.crosscert.fidoadmin.schema;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * src/test/resources/schema-columns.txt 를 테이블 → 컬럼 집합으로 파싱한다.
 * 이 파일은 저장소에 올리지 않는다. 없으면 {@link #available()} 가 false 다.
 */
public final class SchemaColumns {

    private static final Pattern TABLE = Pattern.compile("^## ([A-Z0-9_]+) ");
    private static final Pattern COLUMN = Pattern.compile("^\\s*\\d+\\.\\s+([A-Z0-9_]+)\\s");

    private static final String RESOURCE = "/schema-columns.txt";

    private SchemaColumns() {}

    /** 대조용 컬럼표가 클래스패스에 있는가. */
    public static boolean available() {
        return SchemaColumns.class.getResource(RESOURCE) != null;
    }

    public static Map<String, Set<String>> load() {
        try (InputStream in = SchemaColumns.class.getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("schema-columns.txt not found");
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Set<String>> result = new LinkedHashMap<>();
            String current = null;
            for (String line : text.split("\n")) {
                Matcher t = TABLE.matcher(line);
                if (t.find()) { current = t.group(1); result.put(current, new LinkedHashSet<>()); continue; }
                Matcher c = COLUMN.matcher(line);
                if (current != null && c.find()) result.get(current).add(c.group(1));
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
