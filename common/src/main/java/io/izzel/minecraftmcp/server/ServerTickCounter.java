package io.izzel.minecraftmcp.server;

import net.minecraft.util.Util;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerTickCounter {
    private static final AtomicLong TICKS = new AtomicLong();

    private ServerTickCounter() {
    }

    public static void onServerTick() {
        TICKS.incrementAndGet();
        synchronized (TICKS) {
            TICKS.notifyAll();
        }
    }

    public static long current() {
        return TICKS.get();
    }

    public static long await(long ticks, long timeoutMs) {
        long start = TICKS.get();
        long target = start + Math.max(0, ticks);
        long deadline = Util.getNanos() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMs));
        synchronized (TICKS) {
            while (TICKS.get() < target) {
                long remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - Util.getNanos());
                if (remainingMs <= 0) {
                    break;
                }
                try {
                    TICKS.wait(Math.min(remainingMs, 50L));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return TICKS.get() - start;
    }
}
