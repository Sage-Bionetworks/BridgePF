package org.sagebionetworks.bridge.models.studies;

import javax.annotation.Nonnull;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

/**
 * Consent signature image. Includes fields such as MIME type and a Base64 encoded String of the image bits. The image
 * is the handwritten consent signature. The MIME type is used to display the image (such as when we email the consent
 * document).
 */
public class ConsentSignatureImage implements BridgeEntity {
    private static final String DATA_FIELD = "data";
    private static final String MIME_TYPE_FIELD = "mimeType";

    private final @Nonnull String data;
    private final @Nonnull String mimeType;

    /**
     * Simple constructor with validation.
     *
     * @param data
     *         image data as a Base64 encoded string, must be non-null and non-empty
     * @param mimeType
     *         image MIME type (ex: image/png), must be non-null and non-empty
     */
    public ConsentSignatureImage(@Nonnull String data, @Nonnull String mimeType) {
        Preconditions.checkNotNull(data, "data must be non-null");
        Preconditions.checkArgument(!data.isEmpty(), "data must be non-empty");
        Preconditions.checkNotNull(mimeType, "mimeType must be non-null");
        Preconditions.checkArgument(!mimeType.isEmpty(), "mimeType must be non-empty");

        this.data = data;
        this.mimeType = mimeType;
    }

    /**
     * Construct from JSON. This method validates, but throws InvalidEntityException on invalid input. See constructor
     * documentation for details
     *
     * @param jsonNode
     *         JSON node to parse
     * @return consent signature image
     * @throws InvalidEntityException
     *         if the JSON contains invalid fields
     */
    public static ConsentSignatureImage fromJson(JsonNode jsonNode) throws InvalidEntityException {
        String data = JsonUtils.asText(jsonNode, DATA_FIELD);
        String mimeType = JsonUtils.asText(jsonNode, MIME_TYPE_FIELD);

        if (data == null || data.isEmpty()) {
            throw new InvalidEntityException("data must be specified");
        }
        if (mimeType == null || mimeType.isEmpty()) {
            throw new InvalidEntityException("mimeType must be specified");
        }

        return new ConsentSignatureImage(data, mimeType);
    }

    /** Image data as a Base64 encoded string. */
    public @Nonnull String getData() {
        return data;
    }

    /** Image MIME type (ex: image/png) */
    public @Nonnull String getMimeType() {
        return mimeType;
    }
}
