package org.sagebionetworks.bridge.models.subpopulations;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateTimeToLongDeserializer;
import org.sagebionetworks.bridge.json.DateTimeToLongSerializer;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(builder=ConsentSignature.Builder.class)
@JsonFilter("filter")
public final class ConsentSignature implements BridgeEntity {

    public static final ObjectWriter SIGNATURE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("consentCreatedOn")));
    
    // Can't create a custom ObjectReader, in these cases we create a static forJSON method.
    public static ConsentSignature fromJSON(JsonNode node) {
        return new ConsentSignature.Builder()
                .withName(JsonUtils.asText(node, "name"))
                .withBirthdate(JsonUtils.asText(node, "birthdate"))
                .withImageData(JsonUtils.asText(node, "imageData"))
                .withImageMimeType(JsonUtils.asText(node, "imageMimeType"))
                .build();
    }

    private final @Nonnull String name;
    private final @Nonnull String birthdate;
    private final @Nullable String imageData;
    private final @Nullable String imageMimeType;
    private final @Nonnull long consentCreatedOn;
    private final @Nonnull long signedOn;
    private final @Nullable Long withdrewOn;

    /** Private constructor. Instances should be constructed using factory methods create() or createFromJson(). */
    private ConsentSignature(String name, String birthdate, String imageData, String imageMimeType,
            long consentCreatedOn, long signedOn, Long withdrewOn) {
        this.name = name;
        this.birthdate = birthdate;
        this.imageData = imageData;
        this.imageMimeType = imageMimeType;
        this.consentCreatedOn = consentCreatedOn;
        this.signedOn = signedOn;
        this.withdrewOn = withdrewOn;
    }

    /** Name of the user giving consent. */
    public @Nonnull String getName() {
        return name;
    }

    /** User's birthday in the format "YYYY-MM-DD". */
    public @Nonnull String getBirthdate() {
        return birthdate;
    }

    /** Image data as a Base64 encoded string. */
    public @Nullable String getImageData() {
        return imageData;
    }

    /** Image MIME type (ex: image/png). */
    public @Nullable String getImageMimeType() {
        return imageMimeType;
    }
    
    /**
     * The timestamp of the consent the user has signed. May not be the timestamp of the currently active version of the
     * consent.
     */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public @Nonnull long getConsentCreatedOn() {
        return consentCreatedOn;
    }
    
    /** The date and time recorded for this signature. */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public @Nonnull long getSignedOn() {
        return signedOn;
    }
    
    /** The date and time the user withdrew this consent (can be null if active). */
    @JsonSerialize(using = DateTimeToLongSerializer.class)
    public @Nullable Long getWithdrewOn() {
        return withdrewOn;
    }

    @Override
    public int hashCode() {
        return Objects.hash(birthdate, imageData, imageMimeType, name, consentCreatedOn, signedOn, withdrewOn);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        ConsentSignature other = (ConsentSignature) obj;
        return Objects.equals(birthdate, other.birthdate) && Objects.equals(imageData, other.imageData)
                && Objects.equals(imageMimeType, other.imageMimeType) && Objects.equals(name, other.name) 
                && Objects.equals(consentCreatedOn, other.consentCreatedOn) && Objects.equals(signedOn, other.signedOn) 
                && Objects.equals(withdrewOn, other.withdrewOn);
    }

    @Override
    public String toString() {
        return String.format("ConsentSignature [name=%s, birthdate=%s, imageData=%s, imageMimeType=%s, consentCreatedOn=%s, signedOn=%s, withdrewOn=%s]", 
                name, birthdate, imageData, imageMimeType, consentCreatedOn, signedOn, withdrewOn);
    }
    
    public static class Builder {
        private String name;
        private String birthdate;
        private String imageData;
        private String imageMimeType;
        private long consentCreatedOn;
        private long signedOn;
        private Long withdrewOn;
        
        public Builder withConsentSignature(ConsentSignature signature) {
            checkNotNull(signature);
            this.name = signature.name;
            this.birthdate = signature.birthdate;
            this.imageData = signature.imageData;
            this.imageMimeType = signature.imageMimeType;
            this.consentCreatedOn = signature.consentCreatedOn;
            this.signedOn = signature.signedOn;
            this.withdrewOn = signature.withdrewOn;
            return this;
        }
        public Builder withName(String name) {
            this.name = name;
            return this;
        }
        public Builder withBirthdate(String birthdate) {
            this.birthdate = birthdate;
            return this;
        }
        public Builder withImageData(String imageData) {
            this.imageData = imageData;
            return this;
        }
        public Builder withImageMimeType(String imageMimeType) {
            this.imageMimeType = imageMimeType;
            return this;
        }
        @JsonDeserialize(using = DateTimeToLongDeserializer.class)
        public Builder withConsentCreatedOn(long consentCreatedOn) {
            this.consentCreatedOn = consentCreatedOn;
            return this;
        }
        @JsonDeserialize(using = DateTimeToLongDeserializer.class)
        public Builder withSignedOn(long signedOn) {
            this.signedOn = signedOn;
            return this;
        }
        public Builder withWithdrewOn(Long withdrewOn) {
            this.withdrewOn = withdrewOn;
            return this;
        }
        public ConsentSignature build() {
            long signatureTime = (signedOn > 0L) ? signedOn : DateTime.now().getMillis();
            ConsentSignature signature = new ConsentSignature(name, birthdate, imageData, imageMimeType,
                    consentCreatedOn, signatureTime, withdrewOn);
            return signature;
        }
        
    }
    
}
