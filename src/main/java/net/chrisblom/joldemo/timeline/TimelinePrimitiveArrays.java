package net.chrisblom.joldemo.timeline;

import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.StringJoiner;

public record TimelinePrimitiveArrays(long[] windowsStart, long[] windowsEnd,
                                      int[] windowsValue) implements Timeline {

    @Override
    public Optional<Integer> valueAtTime(Instant time) {
        var timeMillis = time.toEpochMilli();
        for (int i = 0; i < windowsStart.length; i++) {
            if (windowsStart[i] <= timeMillis && timeMillis < windowsEnd[i]) {
                return Optional.of(windowsValue[i]);
            }
        }
        return Optional.empty();
    }

    public static TimelinePrimitiveArrays fromTimeLine(TimelineSimple timeline) {
        var starts = timeline.windows().stream().mapToLong(x -> x.start().toEpochMilli()).toArray();
        var ends = timeline.windows().stream().mapToLong(x -> x.end().toEpochMilli()).toArray();
        var values = timeline.windows().stream().mapToInt(x -> x.value()).toArray();

        return new TimelinePrimitiveArrays(
                starts,
                ends,
                values
        );
    }

    // We need to implement hashCode, equals and toString so to support arrays
    // record classes don't use the contents of the array by default
    // Luckily IntelliJ can generate the correct implementations

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TimelinePrimitiveArrays that = (TimelinePrimitiveArrays) o;
        return Arrays.equals(windowsStart, that.windowsStart) && Arrays.equals(windowsEnd, that.windowsEnd) && Arrays.equals(windowsValue, that.windowsValue);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(windowsStart);
        result = 31 * result + Arrays.hashCode(windowsEnd);
        result = 31 * result + Arrays.hashCode(windowsValue);
        return result;
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", TimelinePrimitiveArrays.class.getSimpleName() + "[", "]")
                .add("windowsStart=" + Arrays.toString(windowsStart))
                .add("windowsEnd=" + Arrays.toString(windowsEnd))
                .add("windowsValue=" + Arrays.toString(windowsValue))
                .toString();
    }
}
