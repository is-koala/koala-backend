package com.exskylab.koala.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class RunbookSql {

    public static final Path PAYMENTS = Path.of("docs/runbooks/payments.md");

    private static final Pattern SQL_BLOCK = Pattern.compile("```sql\\n(.*?)```", Pattern.DOTALL);

    private RunbookSql() {
    }

    public static String theOnlyBlock(Path runbook, Predicate<String> selector, String describedAs) {
        List<String> matching = new ArrayList<>();
        Matcher blocks = SQL_BLOCK.matcher(read(runbook));
        while (blocks.find()) {
            String block = blocks.group(1).strip();
            if (selector.test(block)) {
                matching.add(block);
            }
        }
        assertEquals(1, matching.size(),
                "Runbook, " + describedAs + " tam olarak bir SQL blogunda tarif etmeli, bulunan: " + matching.size());
        return matching.getFirst();
    }

    private static String read(Path runbook) {
        try {
            return Files.readString(runbook, StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            throw new IllegalStateException(runbook + " okunamadi", unreadable);
        }
    }
}
