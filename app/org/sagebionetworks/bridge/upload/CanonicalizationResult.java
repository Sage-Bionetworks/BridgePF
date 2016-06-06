package org.sagebionetworks.bridge.upload;

import com.fasterxml.jackson.databind.JsonNode;

/** Encapsulates info related to canonicalizing upload data. */
public class CanonicalizationResult {
    private final JsonNode canonicalizedValueNode;
    private final String errorMessage;
    private final boolean isValid;

    /** Makes a successful canonicalization with the canonicalized JSON node. */
    public static CanonicalizationResult makeResult(JsonNode canonicalizedValueNode) {
        return new CanonicalizationResult(canonicalizedValueNode, null, true);
    }

    /** Makes a failed canonicalization with the error message. */
    public static CanonicalizationResult makeError(String errorMessage) {
        return new CanonicalizationResult(null, errorMessage, false);
    }

    /** Private constructor. Use the static factory methods {@link #makeResult} and {@link #makeError} */
    private CanonicalizationResult(JsonNode canonicalizedValueNode, String errorMessage, boolean isValid) {
        this.canonicalizedValueNode = canonicalizedValueNode;
        this.errorMessage = errorMessage;
        this.isValid = isValid;
    }

    /** The canonicalized JSON node. Null if the canonicalization failed. */
    public JsonNode getCanonicalizedValueNode() {
        return canonicalizedValueNode;
    }

    /** The error message. Null if the canonicalization succeeded. */
    public String getErrorMessage() {
        return errorMessage;
    }

    /** True if the canonicalization succeeded. False if it failed. */
    public boolean isValid() {
        return isValid;
    }
}
