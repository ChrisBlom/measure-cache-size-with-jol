package net.chrisblom.joldemo.timeline;

import java.time.Instant;
import java.util.Optional;

public interface Timeline {
    Optional<Integer> valueAtTime(Instant time);
}
