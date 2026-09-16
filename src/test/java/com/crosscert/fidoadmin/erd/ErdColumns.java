package com.crosscert.fidoadmin.erd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** src/test/resources/erd-columns.txt 를 테이블 → 컬럼 집합으로 파싱한다. */
public final class ErdColumns {

    private static final Pattern TABLE = Pattern.compile("^## ([A-Z0-9_]+) ");
    private static final Pattern COLUMN = Pattern.compile("^\\s*\\d+\\.\\s+([A-Z0-9_]+)\\s");

    private ErdColumns() {}

    public static Map<String, Set<String>> load() {
        try (InputStream in = ErdColumns.class.getResourceAsStream("/erd-columns.txt")) {
            if (in == null) throw new IllegalStateException("erd-columns.txt not found");
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
