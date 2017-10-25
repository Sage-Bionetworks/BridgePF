package org.sagebionetworks.bridge.models.accounts;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

@Embeddable
public class Phone {
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private String number;
    private String regionCode;
    
    // For Hibernate. 
    Phone() {
    }
    
    @JsonCreator
    public Phone(@JsonProperty("number") String number, @JsonProperty("regionCode") String regionCode) {
        this.number = number;
        this.regionCode = regionCode;
    }
    
    @Column(name="phone", length=20)
    public String getNumber() {
        return number;
    }
    public void setNumber(String number) {
        this.number = number;
    }

    @Column(name="phoneRegion", length=2)
    public String getRegionCode() {
        return regionCode;
    }
    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }
    
    /**
     * Returns the phone number formatted in E164 format, or null if the phone number 
     * cannot be parsed into a valid phone number.
     */
    @Transient
    @JsonIgnore
    public String getCanonicalPhone() {
        String formattedNumber = null;
        if (number != null && regionCode != null) {
            try {
                PhoneNumber phoneNumber = PHONE_UTIL.parse(number, regionCode);
                if (PHONE_UTIL.isValidNumber(phoneNumber)) {
                    formattedNumber = PHONE_UTIL.format(phoneNumber, PhoneNumberFormat.E164);
                }
            } catch (NumberParseException e) {
            }
        }
        return formattedNumber;
    }
}
