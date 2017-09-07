package org.sagebionetworks.bridge.models;

public final class RangeTuple<S> {
    private final S start;
    private final S end;
    
    public RangeTuple(S start, S end) {
        this.start = start;
        this.end = end;
    }
    public final S getStart() {
        return start;
    }
    public final S getEnd() {
        return end;
    }
}
