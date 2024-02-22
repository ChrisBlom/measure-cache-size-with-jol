package net.chrisblom.joldemo;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import net.chrisblom.joldemo.timeline.Timeline;
import net.chrisblom.joldemo.timeline.TimelineIdiomatic;
import net.chrisblom.joldemo.timeline.TimelinePrimitive;
import net.chrisblom.joldemo.timeline.TimelinePrimitiveArrays;
import org.openjdk.jol.info.GraphLayout;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class JolDemo {

    private static final int MAX_SIZE = 100_000;

    private final LoadingCache<Integer, Timeline> cache = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .build(key -> JolDemo.randomTimeline(key));

    private final LoadingCache<Integer, Timeline> cachePrimitiveFields = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .build(key -> TimelinePrimitive.fromTimeLine(JolDemo.randomTimeline(key)));

    private final LoadingCache<Integer, Timeline> cachePrimitiveArrays = Caffeine.newBuilder()
            .maximumSize(MAX_SIZE)
            .build(key -> TimelinePrimitiveArrays.fromTimeLine(JolDemo.randomTimeline(key)));


    static TimelineIdiomatic randomTimeline(int seed) {
        Random random = new Random(seed);
        int size = random.nextInt(10, 30);

        List<TimelineIdiomatic.Window> windows = new ArrayList<>();
        long start = 0;
        for (int i = 0; i < size; i++) {
            long windowLengthMillis = random.nextLong(1000L * 60L);
            long end = start + windowLengthMillis;
            var value = random.nextInt(0, 1000);
            windows.add(new TimelineIdiomatic.Window(
                    Instant.ofEpochMilli(start),
                    Instant.ofEpochMilli(end),
                    value
            ));
            start = end;
        }
        var createdAt = Instant.ofEpochMilli(random.nextLong(0, 10000));
        return new TimelineIdiomatic(createdAt, windows);
    }


    public static void main(String[] args) {

        var demo = new JolDemo();

        // populate the caches
        for (int i = 0; i < MAX_SIZE; i++) {
            demo.cache.get(i);
            demo.cachePrimitiveFields.get(i);
            demo.cachePrimitiveArrays.get(i);
        }

        reportMemoryFootprint("Idiomatic:", demo.cache);
        reportMemoryFootprint("Primitive fields:", demo.cachePrimitiveFields);
        reportMemoryFootprint("Primitive arrays:", demo.cachePrimitiveArrays);
    }

    private static void reportMemoryFootprint(String label, LoadingCache<Integer, Timeline> cache) {
        var bytesIdiomatic = GraphLayout.parseInstance(cache).totalSize();
        System.out.println(label);
        System.out.println("- elements:" + cache.asMap().size());
        System.out.println("- total memory footprint: " + bytesIdiomatic / 1024d / 1024d + " MiB");
        System.out.println("- avg memory / element: " + bytesIdiomatic / (double) cache.asMap().size() + " bytes / element");
    }


}
