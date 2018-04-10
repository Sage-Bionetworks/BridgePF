package org.sagebionetworks.bridge.models;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Tuple<S> {
    private final S left;
    private final S right;
    
    @JsonCreator
    public Tuple(@JsonProperty("left") S left, @JsonProperty("right") S right) {
        this.left = left;
        this.right = right;
    }
    public final S getLeft() {
        return left;
    }
    public final S getRight() {
        return right;
    }
    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
    @SuppressWarnings("rawtypes")
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Tuple other = (Tuple) obj;
        return Objects.equals(left, other.left) && Objects.equals(right, other.right);
    }
    @Override
    public String toString() {
        return "Tuple [left=" + left + ", right=" + right + "]";
    }
    
}
