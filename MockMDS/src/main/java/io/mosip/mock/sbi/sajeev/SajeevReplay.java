package io.mosip.mock.sbi.sajeev;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;

/**
 * SAJEEV #37: replay and frame rate for the MockMDS face stream. MOCK.
 * Off by default, so the upstream default profile works unchanged.
 * Turn on with -Dsajeev.replay.dir=<folder of .jpg frames> and optionally -Dsajeev.replay.fps=<1..60>.
 */
public final class SajeevReplay {

    private static File[] files;
    private static int index;

    private SajeevReplay() {
    }

    /** @return the folder set with sajeev.replay.dir, or null when replay is off */
    private static synchronized File[] files() {
        if (files == null) {
            String dir = System.getProperty("sajeev.replay.dir");
            if (dir == null || dir.isEmpty()) {
                files = new File[0];
            } else {
                File[] found = new File(dir).listFiles((d, n) -> n.toLowerCase().endsWith(".jpg"));
                if (found != null) {
                    Arrays.sort(found);
                }
                files = found == null ? new File[0] : found;
            }
        }
        return files;
    }

    /** @return the next replay frame (cycling), or null when replay is off or empty */
    public static synchronized byte[] nextFrame() {
        File[] f = files();
        if (f.length == 0) {
            return null;
        }
        try {
            byte[] b = Files.readAllBytes(f[index].toPath());
            index = (index + 1) % f.length;
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    private static final ThreadLocal<Long> NEXT_DEADLINE = new ThreadLocal<>();

    /**
     * Sleep until the next frame is due. With sajeev.replay.fps set, frames are scheduled on fixed deadlines, so the
     * rate stays close to the configured value whatever the per-frame work costs. Without it, sleep the upstream 30 ms.
     */
    public static void sleepUntilNextFrame() throws InterruptedException {
        String v = System.getProperty("sajeev.replay.fps");
        if (v == null || v.isEmpty()) {
            Thread.sleep(30L);
            return;
        }
        long interval = frameIntervalMs() * 1_000_000L;
        long now = System.nanoTime();
        Long due = NEXT_DEADLINE.get();
        long next = due == null ? now + interval : due + interval;
        if (next < now - interval) {
            next = now + interval; // fell far behind: resynchronise rather than burst
        }
        NEXT_DEADLINE.set(next);
        long wait = next - now;
        if (wait > 0) {
            Thread.sleep(wait / 1_000_000L, (int) (wait % 1_000_000L));
        }
    }

    /** @return milliseconds to sleep between stream frames: 1000/fps when set, else the upstream 30 ms */
    public static long frameIntervalMs() {
        String v = System.getProperty("sajeev.replay.fps");
        if (v == null || v.isEmpty()) {
            return 30L;
        }
        try {
            int fps = Math.max(1, Math.min(60, Integer.parseInt(v.trim())));
            return 1000L / fps;
        } catch (NumberFormatException e) {
            return 30L;
        }
    }
}
