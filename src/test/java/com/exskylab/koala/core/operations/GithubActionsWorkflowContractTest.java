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
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class GithubActionsWorkflowContractTest {

    private static final Path WORKFLOW = Path.of(".github/workflows/ci.yml");

    @Test
    void githubOnlyFiresTheDokployWebhookOnVersionTags() throws IOException {
        String workflow = workflow();
        Map<?, ?> jobs = jobs();
        Map<?, ?> deploy = job(jobs, "deploy");
        Map<?, ?> trigger = stepNamed(jobs, "deploy", "Trigger Dokploy deploy");
        Map<?, ?> concurrency = concurrency();

        assertAll(
                () -> assertEquals(Set.of("deploy"), jobs.keySet()),
                () -> assertEquals("ubuntu-latest", deploy.get("runs-on")),
                () -> assertEquals(5, deploy.get("timeout-minutes")),
                () -> assertNull(deploy.get("needs")),
                () -> assertEquals(
                        "startsWith(github.ref, 'refs/tags/v')", deploy.get("if")),
                () -> assertEquals(
                        "curl -fsSL -X POST \"${{ secrets.DOKPLOY_DEPLOY_WEBHOOK }}\"",
                        trigger.get("run")),
                () -> assertEquals(List.of(), actionReferences()),
                () -> assertFalse(workflow.contains("self-hosted")),
                () -> assertFalse(workflow.contains("pull_request:")),
                () -> assertFalse(workflow.contains("branches: [main]")),
                () -> assertTrue(workflow.contains("tags: ['v*']")),
                () -> assertTrue(workflow.contains("workflow_dispatch:")),
                () -> assertEquals(
                        "ci-${{ github.workflow }}-${{ github.ref }}", concurrency.get("group")),
                () -> assertEquals(false, concurrency.get("cancel-in-progress")));
    }

    @Test
    void githubDoesNotBuildImagesOrRunMaven() throws IOException {
        String workflow = workflow();
        assertAll(
                () -> assertFalse(workflow.contains("docker/")),
                () -> assertFalse(workflow.contains("setup-java")),
                () -> assertFalse(workflow.contains("mvnw")),
                () -> assertFalse(workflow.contains("qemu")));
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
