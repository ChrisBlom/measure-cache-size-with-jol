package net.chrisblom.joldemo.timeline;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public record TimelineSimple(Instant createdAt, List<Window> windows) implements Timeline {

    public record Window(Instant start, Instant end, int value) {
    }

    @Override
    public Optional<Integer> valueAtTime(Instant time) {
        for (Window w : windows) {
            if (w.start.compareTo(time) <= 0 && time.compareTo(w.end) < 0) {
                return Optional.of(w.value);
            }
        }
        return Optional.empty();
    }
}
