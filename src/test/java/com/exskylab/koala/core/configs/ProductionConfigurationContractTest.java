package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.configs.ProductionConfigurationGuard.Enforcement;
import com.exskylab.koala.core.configs.ProductionConfigurationGuard.Rule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionConfigurationContractTest {

    private static final Pattern ENVIRONMENT_VARIABLE = Pattern.compile("`([A-Z][A-Z0-9_]{2,})`");

    private static final String ENVIRONMENTS = "docs/environments.md";

    private static final String CLAUDE = "CLAUDE.md";

    @Test
    void everyRuleTheGuardEnforcesIsInTheEnvironmentsTableWithTheOutcomeItActuallyHas() throws IOException {
        List<String> rows = tableRows(section(ENVIRONMENTS, "## Production zorunlulukları"));

        for (Rule rule : Rule.values()) {
            String row = rows.stream()
                    .filter(candidate -> variablesIn(cell(candidate, 0)).contains(rule.variable()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError(rule.variable() + " kurali " + ENVIRONMENTS
                            + " §Production zorunluluklari tablosunda yok; guard durduruyor ya da uyariyor "
                            + "ama operatorun okudugu tablo bunu soylemiyor."));

            assertTrue(cell(row, 2).contains(outcomeWordIn(ENVIRONMENTS, rule)),
                    rule + " icin beklenen sonuc satirda yazili degil: " + row);
        }
    }

    @Test
    void theEnvironmentsTableCarriesNoRuleTheGuardDoesNotEnforce() throws IOException {
        Set<String> documented = tableRows(section(ENVIRONMENTS, "## Production zorunlulukları")).stream()
                .flatMap(row -> variablesIn(cell(row, 0)).stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertEquals(catalogueVariables(), documented,
                "Tablo guard'in kural listesinden ayrismis; fazladan satir kimsenin uygulamadigi bir soz verir.");
    }

    @Test
    void everyRuleTheGuardEnforcesHasItsOwnRowInTheDecisionTable() throws IOException {
        List<String> rows = tableRows(section(CLAUDE, "## 2ax."));

        for (Rule rule : Rule.values()) {
            List<String> matching = rows.stream()
                    .filter(row -> variablesIn(cell(row, 0)).contains(rule.variable()))
                    .filter(row -> cell(row, 1).contains(outcomeWordIn(CLAUDE, rule)))
                    .toList();

            assertEquals(1, matching.size(), rule + " icin " + CLAUDE + " §2ax tablosunda tam olarak bir satir "
                    + "olmali; gerekce yazilmadan eklenen kural sessizce gecer. Bulunan: " + matching);
        }

        assertEquals(Rule.values().length, rows.size(),
                "§2ax tablosu guard'in kural sayisindan ayrismis: " + rows);
    }

    @Test
    void theDokployItemNamesEveryVariableTheGuardOnlyWarnsAbout() throws IOException {
        String item = Files.readString(Path.of(CLAUDE), StandardCharsets.UTF_8).lines()
                .filter(line -> line.startsWith("11. **Dokploy'da"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("CLAUDE.md 11. maddesi bulunamadi."));

        for (Rule rule : Rule.values()) {
            if (rule.enforcement() == Enforcement.WARNS) {
                assertTrue(item.contains(rule.variable()), rule.variable() + " yalnizca WARN uretiyor, yani "
                        + "duzeltmesi operatorde; 11. madde onu saymali: " + item);
            }
        }
    }

    @Test
    void theMarkerTheOperatorGrepsForIsTheOneTheGuardWrites() throws IOException {
        assertTrue(Files.readString(Path.of(ENVIRONMENTS), StandardCharsets.UTF_8)
                        .contains(ProductionConfigurationGuard.DRIFT_MARKER),
                ENVIRONMENTS + " operatore aranacak isareti veriyor; guard baska bir isaret yaziyorsa "
                        + "arama bos doner.");
    }

    private static Set<String> catalogueVariables() {
        return Arrays.stream(Rule.values())
                .map(Rule::variable)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static String outcomeWordIn(String document, Rule rule) {
        if (rule.enforcement() == Enforcement.REFUSES) {
            return "durdurur";
        }
        return CLAUDE.equals(document) ? "uyarır" : "WARN";
    }

    private static String section(String document, String heading) throws IOException {
        String text = Files.readString(Path.of(document), StandardCharsets.UTF_8);
        int start = text.indexOf(heading);
        assertTrue(start >= 0, document + " icinde " + heading + " basligi yok.");
        int end = text.indexOf("\n## ", start + heading.length());
        return end < 0 ? text.substring(start) : text.substring(start, end);
    }

    private static List<String> tableRows(String section) {
        return section.lines()
                .map(String::trim)
                .filter(line -> line.startsWith("|"))
                .filter(line -> !line.replace("|", "").replace("-", "").replace(" ", "").isEmpty())
                .filter(line -> !variablesIn(cell(line, 0)).isEmpty())
                .toList();
    }

    private static String cell(String row, int index) {
        String[] cells = row.split("\\|");
        return index + 1 < cells.length ? cells[index + 1] : "";
    }

    private static Set<String> variablesIn(String text) {
        Set<String> found = new LinkedHashSet<>();
        Matcher matcher = ENVIRONMENT_VARIABLE.matcher(text);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }
}
