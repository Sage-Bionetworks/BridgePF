package org.sagebionetworks.bridge.models.studies;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Preconditions;

import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

public class ConsentSignature implements BridgeEntity {

    private static final String NAME_FIELD = "name";
    private static final String BIRTHDATE_FIELD = "birthdate";
    private static final String IMAGE_FIELD = "image";

    private final @Nonnull String name;
    private final @Nonnull String birthdate;
    private final @Nullable ConsentSignatureImage image;

    /**
     * <p>
     * Simple constructor.
     * </p>
     * <p>
     * Signature image is nullable for backwards compatibility with the old format. ConsentController will ensure that
     * all new signatures are created with a signature image. After the beta data is wiped, we can update this
     * constructor to enforce non-null signature image.
     * </p>
     *
     * @param name
     *         name of the user giving consent, must be non-null and non-empty
     * @param birthdate
     *         user's birthday in the format "YYYY-MM-DD", must be non-null and non-empty
     * @param image
     *         signature image, may be null, must be non-empty
     */
    public ConsentSignature(@Nonnull String name, @Nonnull String birthdate, @Nullable ConsentSignatureImage image) {
        Preconditions.checkNotNull(name, "name must be non-null");
        Preconditions.checkArgument(!name.isEmpty(), "name must be non-empty");
        Preconditions.checkNotNull(birthdate, "birthdate must be non-null");
        Preconditions.checkArgument(!birthdate.isEmpty(), "birthdate must be non-empty");

        this.name = name;
        this.birthdate = birthdate;
        this.image = image;
    }

    /**
     * Construct from JSON. This method validates, but throws InvalidEntityException on invalid input. See constructor
     * documentation for details
     *
     * @param node
     *         JSON node to parse
     * @return consent signature
     * @throws InvalidEntityException
     *         if the JSON contains invalid fields
     */
    public static ConsentSignature fromJson(JsonNode node) throws InvalidEntityException {
        String name = JsonUtils.asText(node, NAME_FIELD);
        String birthdate = JsonUtils.asText(node, BIRTHDATE_FIELD);
        ObjectNode imageNode = JsonUtils.asObjectNode(node, IMAGE_FIELD);

        if (name == null || name.isEmpty()) {
            throw new InvalidEntityException("name must be specified");
        }
        if (birthdate == null || birthdate.isEmpty()) {
            throw new InvalidEntityException("birthdate must be specified");
        }

        ConsentSignatureImage sigImg = null;
        if (imageNode != null) {
            sigImg = ConsentSignatureImage.fromJson(imageNode);
        }

        return new ConsentSignature(name, birthdate, sigImg);
    }

    /** Name of the user giving consent. */
    public @Nonnull String getName() {
        return name;
    }

    /** User's birthday in the format "YYYY-MM-DD". */
    public @Nonnull String getBirthdate() {
        return birthdate;
    }

    /** Signature image. */
    public @Nullable ConsentSignatureImage getImage() {
        return image;
    }
}
