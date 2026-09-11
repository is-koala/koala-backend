package com.exskylab.koala.entities;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDeleteCoverageTest {

    private static final Pattern ENTITY = Pattern.compile("^@Entity", Pattern.MULTILINE);

    private static final Set<String> HARD_DELETE_BY_DESIGN = Set.of(
            "PasswordResetToken", "PendingRegistration");

    private static final Set<String> INHERITS_FROM_ROOT = Set.of(
            "File", "Image", "VerificationDocument",
            "EmailVerification", "PhoneVerification", "IdentityVerification");

    // CompanyContact BaseEntity + @SQLDelete taşıyor; muafiyet bayattı (#88).
    private static final Set<String> NOT_AUDITED = Set.of("JobCategory");

    @Test
    void everyAuditedEntityIsSoftDeleted() throws IOException {
        List<String> missing = new ArrayList<>();

        for (Path file : entityFiles()) {
            String name = file.getFileName().toString().replace(".java", "");
            String source = Files.readString(file, StandardCharsets.UTF_8);

            if (!ENTITY.matcher(source).find()) {
                continue;
            }
            if (HARD_DELETE_BY_DESIGN.contains(name) || INHERITS_FROM_ROOT.contains(name)
                    || NOT_AUDITED.contains(name) || !source.contains("extends BaseEntity")) {
                continue;
            }

            if (!source.contains("@SQLDelete") || !source.contains("@SQLRestriction")) {
                missing.add(name);
            }
        }

        assertTrue(missing.isEmpty(),
                "Soft delete tasimayan entity'ler: " + missing
                        + "\nYeni bir entity eklediysen @SQLDelete + @SQLRestriction ekle "
                        + "ya da bilinerek haric tutuluyorsa bu testteki listeye yaz.");
    }

    @Test
    void softDeletedEntitiesNeverIssueARealDelete() throws IOException {
        List<String> wrong = new ArrayList<>();

        for (Path file : entityFiles()) {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!source.contains("@SQLDelete")) {
                continue;
            }
            String annotation = source.substring(source.indexOf("@SQLDelete"));
            annotation = annotation.substring(0, annotation.indexOf(")") + 1);

            if (!annotation.contains("UPDATE") || !annotation.contains("is_deleted = true")) {
                wrong.add(file.getFileName().toString());
            }
        }

        assertTrue(wrong.isEmpty(), "@SQLDelete gercek DELETE calistiriyor: " + wrong);
    }

    @Test
    void theExclusionListStaysHonest() throws IOException {
        List<String> stale = new ArrayList<>();

        for (String excluded : HARD_DELETE_BY_DESIGN) {
            Path file = entityPath(excluded);
            if (!Files.exists(file)) {
                stale.add(excluded + " (yok)");
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (source.contains("@SQLDelete")) {
                stale.add(excluded + " (artik soft-delete)");
            }
        }

        for (String excluded : INHERITS_FROM_ROOT) {
            Path file = entityPath(excluded);
            if (!Files.exists(file)) {
                stale.add(excluded + " (yok)");
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (!inheritsSoftDeleteFromRoot(source)) {
                stale.add(excluded + " (kokten soft-delete miras almiyor)");
            }
        }

        for (String excluded : NOT_AUDITED) {
            Path file = entityPath(excluded);
            if (!Files.exists(file)) {
                stale.add(excluded + " (yok)");
                continue;
            }
            String source = Files.readString(file, StandardCharsets.UTF_8);
            if (notAuditedExemptionIsStale(source)) {
                stale.add(excluded + " (artik BaseEntity + @SQLDelete)");
            }
        }

        assertTrue(stale.isEmpty(),
                "Muafiyet bayatladi; gerekce artik gecerli degil: " + stale);
    }

    @Test
    void aNotAuditedExemptionGoesStaleOnceTheEntityIsSoftDeleted() {
        String source = """
                @Entity
                @SQLDelete(sql = "UPDATE example SET is_deleted = true WHERE id = ?")
                public class Example extends BaseEntity {}
                """;

        assertTrue(notAuditedExemptionIsStale(source),
                "BaseEntity + @SQLDelete tasuyan bir NOT_AUDITED kaydi bayat sayilmaliydi.");
    }

    static boolean notAuditedExemptionIsStale(String source) {
        return source.contains("extends BaseEntity") && source.contains("@SQLDelete");
    }

    private static boolean inheritsSoftDeleteFromRoot(String source) {
        if (source.contains("@SQLDelete")) {
            return false;
        }
        return source.contains("extends Media") || source.contains("extends UserVerification");
    }

    private static Path entityPath(String name) {
        return Path.of("src", "main", "java", "com", "exskylab", "koala",
                "entities", name + ".java");
    }

    private List<Path> entityFiles() throws IOException {
        try (Stream<Path> files = Files.walk(Path.of("src", "main", "java", "com",
                "exskylab", "koala", "entities"))) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
