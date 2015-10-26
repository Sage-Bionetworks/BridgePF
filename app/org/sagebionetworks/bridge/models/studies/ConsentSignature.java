package org.sagebionetworks.bridge.models.studies;

import java.util.Objects;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.validators.ConsentSignatureValidator;
import org.sagebionetworks.bridge.validators.Validate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

@JsonDeserialize(builder=ConsentSignature.Builder.class)
@JsonFilter("filter")
public final class ConsentSignature implements BridgeEntity {

    public static final ObjectWriter SIGNATURE_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
            SimpleBeanPropertyFilter.serializeAllExcept("signedOn")));

    private static final ConsentSignatureValidator VALIDATOR = new ConsentSignatureValidator();

    private final @Nonnull String name;
    private final @Nonnull String birthdate;
    private final @Nullable String imageData;
    private final @Nullable String imageMimeType;
    private final @Nonnull long signedOn;

    /** Private constructor. Instances should be constructed using factory methods create() or createFromJson(). */
    @JsonCreator
    private ConsentSignature(@JsonProperty("name") String name, @JsonProperty("birthdate") String birthdate,
            @JsonProperty("imageData") String imageData, @JsonProperty("imageMimeType") String imageMimeType, 
            @JsonProperty("signedOn") long signedOn) {

        this.name = name;
        this.birthdate = birthdate;
        this.imageData = imageData;
        this.imageMimeType = imageMimeType;
        this.signedOn = signedOn;
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
    
    /** The date and time recorded for this signature. */
    public long getSignedOn() {
        return signedOn;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + Objects.hashCode(birthdate);
        result = prime * result + Objects.hashCode(imageData);
        result = prime * result + Objects.hashCode(imageMimeType);
        result = prime * result + Objects.hashCode(name);
        result = prime * result + Objects.hashCode(signedOn);
        return result;
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
                && Objects.equals(signedOn, other.signedOn);
    }

    @Override
    public String toString() {
        return String.format("ConsentSignature [name=%s, birthdate=%s, imageData=%s, imageMimeType=%s, signedOn=%s]", 
                name, birthdate, imageData, imageMimeType, signedOn);
    }
    
    public static class Builder {
        private String name;
        private String birthdate;
        private String imageData;
        private String imageMimeType;
        private long signedOn;
        
        public Builder withConsentSignature(ConsentSignature signature) {
            this.name = signature.name;
            this.birthdate = signature.birthdate;
            this.imageData = signature.imageData;
            this.imageMimeType = signature.imageMimeType;
            this.signedOn = signature.signedOn;
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
        public Builder withSignedOn(long signedOn) {
            this.signedOn = signedOn;
            return this;
        }
        public ConsentSignature build() {
            long signatureTime = (signedOn > 0L) ? signedOn : DateTime.now().getMillis();
            ConsentSignature signature = new ConsentSignature(name, birthdate, imageData, imageMimeType, signatureTime);
            Validate.entityThrowingException(VALIDATOR, signature);
            return signature;
        }
        
    }
    
}
