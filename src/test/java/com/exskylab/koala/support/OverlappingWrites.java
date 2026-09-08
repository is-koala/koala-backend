package com.exskylab.koala.support;

import com.exskylab.koala.core.helpers.ConstraintViolations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class OverlappingWrites {

    public static final String NO_CONSTRAINT = "no-constraint";

    private static final String NEVER_OVERLAPPED =
            "Iki yazma ayni anda acik transaction icinde olmaliydi; ortusmediyse test "
                    + "'indeks unique mi' seviyesine dusmus olur: ";

    private final CyclicBarrier bothInsideTheirTransaction = new CyclicBarrier(2);
    private final List<String> barrierFailures = Collections.synchronizedList(new ArrayList<>());

    public void meet() {
        try {
            bothInsideTheirTransaction.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException waiting) {
            barrierFailures.add(waiting.getClass().getSimpleName());
            throw new IllegalStateException("the two writes never overlapped", waiting);
        }
    }

    public List<String> outcomesOf(Callable<String> first, Callable<String> second) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<String> one = executor.submit(first);
            Future<String> two = executor.submit(second);
            try {
                List<String> outcomes = List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
                assertOverlapped();
                return outcomes;
            } catch (ExecutionException failed) {
                assertOverlapped();
                throw failed;
            }
        }
    }

    public static String constraintNameOf(Throwable exception) {
        String name = ConstraintViolations.nameOf(exception);
        return name == null ? NO_CONSTRAINT : name;
    }

    private void assertOverlapped() {
        assertTrue(barrierFailures.isEmpty(), NEVER_OVERLAPPED + barrierFailures);
    }
}
