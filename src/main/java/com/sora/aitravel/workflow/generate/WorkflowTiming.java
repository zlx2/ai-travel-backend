package com.sora.aitravel.workflow.generate;

import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

/** Small timing helper for trip generation workflow nodes. */
@Slf4j
public final class WorkflowTiming {

    private WorkflowTiming() {}

    public static void run(String workflow, String node, Runnable action) {
        long start = System.nanoTime();
        try {
            action.run();
        } finally {
            log.info(
                    "行程生成耗时 workflow={} node={} elapsedMs={}",
                    workflow,
                    node,
                    elapsedMs(start));
        }
    }

    public static <T> T call(String workflow, String node, Supplier<T> action) {
        long start = System.nanoTime();
        try {
            return action.get();
        } finally {
            log.info(
                    "行程生成耗时 workflow={} node={} elapsedMs={}",
                    workflow,
                    node,
                    elapsedMs(start));
        }
    }

    public static long start() {
        return System.nanoTime();
    }

    public static long elapsedMs(long start) {
        return Math.max(0L, (System.nanoTime() - start) / 1_000_000L);
    }
}
