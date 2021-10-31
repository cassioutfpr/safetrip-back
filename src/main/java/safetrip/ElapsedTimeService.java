package safetrip;

import java.time.Duration;
import java.time.Instant;

public class ElapsedTimeService {

    private static Instant startedAt;

    public static void start() {
        startedAt = Instant.now();
    }

    public static void printElapsedTime(String tag) {
        Duration timeElapsed = Duration.between(startedAt, Instant.now());
        System.out.println(tag + " " + timeElapsed.toMillis()/1000 +" seconds");
    }
}
