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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GithubActionsWorkflowContractTest {

    private static final Path WORKFLOWS = Path.of(".github/workflows");
    private static final Path VERIFY = WORKFLOWS.resolve("verify.yml");
    private static final Path RELEASE = WORKFLOWS.resolve("release.yml");
    private static final String GATE = "./mvnw -B -ntp verify -Dkoala.test.latch-timeout=30";
    private static final String PUBLISH_MODE = "steps.plan.outputs.mode == 'publish'";

    @Test
    void everyWorkflowRunsOnGithubHostedRunners() throws IOException {
        try (Stream<Path> files = Files.list(WORKFLOWS)) {
            Set<String> names = files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
            assertEquals(Set.of("verify.yml", "release.yml"), names);
        }
        for (Path workflow : List.of(VERIFY, RELEASE)) {
            assertFalse(text(workflow).contains("self-hosted"), workflow + " uses a self-hosted runner");
            for (Object job : jobs(workflow).values()) {
                assertEquals("ubuntu-latest", assertInstanceOf(Map.class, job).get("runs-on"));
            }
        }
    }

    @Test
    void verifyRunsTheFullGateOnPullRequestsAndMain() throws IOException {
        Map<?, ?> root = root(VERIFY);
        Map<?, ?> on = triggers(root);
        Map<?, ?> verify = job(jobs(VERIFY), "verify");
        Map<?, ?> setupJava = stepUsing(verify, "actions/setup-java@");
        Map<?, ?> javaInputs = assertInstanceOf(Map.class, setupJava.get("with"));
        Map<?, ?> concurrency = assertInstanceOf(Map.class, root.get("concurrency"));
        String gate = String.valueOf(stepNamed(verify, "Verify").get("run"));

        assertAll(
                () -> assertEquals(Set.of("pull_request", "push", "workflow_dispatch"), on.keySet()),
                () -> assertEquals(List.of("main"), branches(on, "pull_request")),
                () -> assertEquals(List.of("main"), branches(on, "push")),
                () -> assertEquals(Map.of("contents", "read"), root.get("permissions")),
                () -> assertEquals(GATE, gate),
                () -> assertFalse(gate.contains("skip")),
                () -> assertFalse(gate.contains("-Dtest=")),
                () -> assertFalse(gate.contains("-Dit.test=")),
                () -> assertEquals("temurin", javaInputs.get("distribution")),
                () -> assertEquals("25", javaInputs.get("java-version")),
                () -> assertEquals("maven", javaInputs.get("cache")),
                () -> assertTrue((Integer) verify.get("timeout-minutes") <= 60),
                () -> assertEquals(
                        "${{ github.event_name == 'pull_request' }}", concurrency.get("cancel-in-progress")));
    }

    @Test
    void releaseTagsOnlyAfterThePublishedImageIsCheckedAndDeploysLast() throws IOException {
        Map<?, ?> root = root(RELEASE);
        Map<?, ?> on = triggers(root);
        Map<?, ?> release = job(jobs(RELEASE), "release");
        List<String> order = stepNames(release);
        Map<?, ?> plan = stepNamed(release, "Plan release");
        Map<?, ?> publish = stepNamed(release, "Publish image");
        Map<?, ?> check = stepNamed(release, "Check published image");
        Map<?, ?> tag = stepNamed(release, "Tag release");
        Map<?, ?> deploy = stepNamed(release, "Trigger Dokploy deploy");
        Map<?, ?> push = assertInstanceOf(Map.class, on.get("push"));
        Map<?, ?> concurrency = assertInstanceOf(Map.class, root.get("concurrency"));

        assertAll(
                () -> assertEquals(Set.of("workflow_dispatch", "push"), on.keySet()),
                () -> assertEquals(List.of("v*"), push.get("tags")),
                () -> assertNull(push.get("branches")),
                () -> assertTrue(order.indexOf("Plan release") < order.indexOf("Publish image")),
                () -> assertTrue(order.indexOf("Publish image") < order.indexOf("Check published image")),
                () -> assertTrue(order.indexOf("Check published image") < order.indexOf("Tag release")),
                () -> assertTrue(order.indexOf("Tag release") < order.indexOf("Trigger Dokploy deploy")),
                () -> assertEquals("scripts/publish-ghcr-image.sh --latest", publish.get("run")),
                () -> assertEquals(PUBLISH_MODE, publish.get("if")),
                () -> assertEquals(PUBLISH_MODE, check.get("if")),
                () -> assertEquals(PUBLISH_MODE, tag.get("if")),
                () -> assertNull(deploy.get("if")),
                () -> assertEquals("curl -fsSL -X POST \"$DOKPLOY_DEPLOY_WEBHOOK\"", deploy.get("run")),
                () -> assertTrue(String.valueOf(plan.get("run")).contains("--workflow verify.yml")),
                () -> assertTrue(String.valueOf(plan.get("run")).contains("refs/heads/main")),
                () -> assertFalse(text(RELEASE).contains("build-push-action")),
                () -> assertEquals("release", concurrency.get("group")),
                () -> assertEquals(false, concurrency.get("cancel-in-progress")));
    }

    private static String text(Path workflow) throws IOException {
        return Files.readString(workflow, StandardCharsets.UTF_8);
    }

    private static Map<?, ?> root(Path workflow) throws IOException {
        try (InputStream input = Files.newInputStream(workflow)) {
            return assertInstanceOf(Map.class, new Yaml().load(input));
        }
    }

    private static Map<?, ?> triggers(Map<?, ?> root) {
        return assertInstanceOf(Map.class, root.get(Boolean.TRUE));
    }

    private static List<?> branches(Map<?, ?> on, String event) {
        return assertInstanceOf(List.class, assertInstanceOf(Map.class, on.get(event)).get("branches"));
    }

    private static Map<?, ?> jobs(Path workflow) throws IOException {
        return assertInstanceOf(Map.class, root(workflow).get("jobs"));
    }

    private static Map<?, ?> job(Map<?, ?> jobs, String name) {
        return assertInstanceOf(Map.class, jobs.get(name));
    }

    private static List<Map<?, ?>> steps(Map<?, ?> job) {
        List<?> steps = assertInstanceOf(List.class, job.get("steps"));
        return steps.stream().<Map<?, ?>>map(step -> assertInstanceOf(Map.class, step)).toList();
    }

    private static List<String> stepNames(Map<?, ?> job) {
        return steps(job).stream().map(step -> String.valueOf(step.get("name"))).toList();
    }

    private static Map<?, ?> stepNamed(Map<?, ?> job, String name) {
        return steps(job).stream()
                .filter(step -> name.equals(step.get("name")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step named " + name));
    }

    private static Map<?, ?> stepUsing(Map<?, ?> job, String prefix) {
        return steps(job).stream()
                .filter(step -> step.get("uses") instanceof String uses && uses.startsWith(prefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no step uses " + prefix));
    }
}
