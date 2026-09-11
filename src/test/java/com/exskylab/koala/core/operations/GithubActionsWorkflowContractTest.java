package com.exskylab.koala.core.operations;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GithubActionsWorkflowContractTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/ci.yml");

    @Test
    void usesTheSupportedNode24ActionMajors() throws IOException {
        assertEquals(
                List.of(
                        "actions/checkout@v7",
                        "actions/setup-java@v6",
                        "actions/upload-artifact@v7",
                        "actions/checkout@v7",
                        "docker/setup-qemu-action@v4",
                        "docker/setup-buildx-action@v4",
                        "docker/login-action@v4",
                        "docker/build-push-action@v7"),
                actionReferences());
    }

    @Test
    void keepsTheTestReportAndMavenCacheContract() throws IOException {
        Map<?, ?> jobs = jobs();
        Map<?, ?> setupJava = stepUsing(jobs, "test", "actions/setup-java@v6");
        Map<?, ?> setupJavaInputs = assertInstanceOf(Map.class, setupJava.get("with"));
        Map<?, ?> tests = stepNamed(jobs, "test", "Run tests");
        Map<?, ?> upload = stepUsing(jobs, "test", "actions/upload-artifact@v7");
        Map<?, ?> uploadInputs = assertInstanceOf(Map.class, upload.get("with"));
        String reportPaths = assertInstanceOf(String.class, uploadInputs.get("path"));

        Map<?, ?> buildPush = stepUsing(jobs, "build", "docker/build-push-action@v7");
        Map<?, ?> buildPushInputs = assertInstanceOf(Map.class, buildPush.get("with"));
        String cacheTo = assertInstanceOf(String.class, buildPushInputs.get("cache-to"));

        assertAll(
                () -> assertEquals("maven", setupJavaInputs.get("cache")),
                () -> assertEquals("./mvnw -B verify", tests.get("run")),
                () -> assertEquals(45, job(jobs, "test").get("timeout-minutes")),
                () -> assertEquals(60, job(jobs, "build").get("timeout-minutes")),
                () -> assertEquals(5, job(jobs, "deploy").get("timeout-minutes")),
                () -> assertEquals("always()", upload.get("if")),
                () -> assertEquals(true, upload.get("continue-on-error")),
                () -> assertEquals("test-results", uploadInputs.get("name")),
                () -> assertEquals("ignore", uploadInputs.get("if-no-files-found")),
                () -> assertTrue(reportPaths.contains("target/surefire-reports/")),
                () -> assertTrue(reportPaths.contains("target/failsafe-reports/")),
                () -> assertFalse(reportPaths.contains("jacoco")),
                () -> assertEquals(3, uploadInputs.get("retention-days")),
                () -> assertTrue(cacheTo.contains("ignore-error=true")));
    }

    @Test
    void keepsPullRequestsReadOnlyAndDeploysOnlyVersionTags() throws IOException {
        String workflow = workflow();
        Map<?, ?> jobs = jobs();
        Map<?, ?> concurrency = concurrency();

        assertAll(
                () -> assertTrue(workflow.contains("pull_request:\n    branches: [main]")),
                () -> assertTrue(workflow.contains("workflow_dispatch:")),
                () -> assertEquals(
                        "github.event_name == 'pull_request' || github.event_name == 'workflow_dispatch'",
                        job(jobs, "test").get("if")),
                () -> assertNull(job(jobs, "build").get("needs")),
                () -> assertEquals("github.event_name == 'push'", job(jobs, "build").get("if")),
                () -> assertTrue(workflow.contains("VERSION=main-${GITHUB_SHA::7}")),
                () -> assertTrue(workflow.contains("refs/tags/v")),
                () -> assertTrue(workflow.contains("koala:latest")),
                () -> assertEquals("build", job(jobs, "deploy").get("needs")),
                () -> assertEquals(
                        "startsWith(github.ref, 'refs/tags/v')", job(jobs, "deploy").get("if")),
                () -> assertEquals(
                        "ci-${{ github.workflow }}-${{ github.ref }}", concurrency.get("group")),
                () -> assertEquals(
                        "${{ github.event_name == 'pull_request' }}",
                        concurrency.get("cancel-in-progress")));
    }

    private static String workflow() throws IOException {
        return Files.readString(WORKFLOW, StandardCharsets.UTF_8);
    }

    private static List<String> actionReferences() throws IOException {
        List<String> references = new ArrayList<>();
        for (Object rawJob : jobs().values()) {
            Map<?, ?> job = assertInstanceOf(Map.class, rawJob);
            List<?> steps = assertInstanceOf(List.class, job.get("steps"));
            for (Object rawStep : steps) {
                Map<?, ?> step = assertInstanceOf(Map.class, rawStep);
                if (step.get("uses") instanceof String reference) {
                    references.add(reference);
                }
            }
        }
        return references;
    }

    private static Map<?, ?> jobs() throws IOException {
        return assertInstanceOf(Map.class, root().get("jobs"));
    }

    private static Map<?, ?> concurrency() throws IOException {
        return assertInstanceOf(Map.class, root().get("concurrency"));
    }

    private static Map<?, ?> root() throws IOException {
        try (InputStream input = Files.newInputStream(WORKFLOW)) {
            return assertInstanceOf(Map.class, new Yaml().load(input));
        }
    }

    private static Map<?, ?> job(Map<?, ?> jobs, String name) {
        return assertInstanceOf(Map.class, jobs.get(name));
    }

    private static Map<?, ?> stepUsing(Map<?, ?> jobs, String jobName, String reference) {
        return steps(jobs, jobName).stream()
                .map(rawStep -> assertInstanceOf(Map.class, rawStep))
                .filter(step -> reference.equals(step.get("uses")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(jobName + " job has no " + reference + " step"));
    }

    private static Map<?, ?> stepNamed(Map<?, ?> jobs, String jobName, String name) {
        return steps(jobs, jobName).stream()
                .map(rawStep -> assertInstanceOf(Map.class, rawStep))
                .filter(step -> name.equals(step.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError(jobName + " job has no " + name + " step"));
    }

    private static List<?> steps(Map<?, ?> jobs, String jobName) {
        return assertInstanceOf(List.class, job(jobs, jobName).get("steps"));
    }
}
