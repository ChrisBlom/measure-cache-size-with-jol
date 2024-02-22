package net.chrisblom.joldemo.timeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public record TimelinePrimitive(List<WindowPrimitive> windows) implements Timeline {

    record WindowPrimitive(long start, long end, int value) {
    }

    @Override
    public Optional<Integer> valueAtTime(Instant time) {
        var timeMillis = time.toEpochMilli();
        for (WindowPrimitive w : windows) {
            if (w.start <= timeMillis && timeMillis < w.end) {
                return Optional.of(w.value);
            }
        }
        return Optional.empty();
    }

    public static TimelinePrimitive fromTimeLine(TimelineIdiomatic timeline) {
        return new TimelinePrimitive(timeline.windows()
                .stream()
                .map(x -> new WindowPrimitive(x.start().toEpochMilli(), x.end().toEpochMilli(), x.value())).toList());
    }
}
