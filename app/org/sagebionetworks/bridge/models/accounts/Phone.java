package org.sagebionetworks.bridge.models.accounts;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

@Embeddable
public class Phone {
    
    public static final boolean isValid(Phone phone) {
        try {
            PhoneNumber phoneNumber = PHONE_UTIL.parse(phone.getNumber(), phone.getRegionCode());
            return PHONE_UTIL.isValidNumber(phoneNumber);
        } catch (Exception e) {
        }
        return false;
    }
    
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
        return format(PhoneNumberFormat.E164);
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
    
    @Transient
    public String getNationalFormat() {
        return format(PhoneNumberFormat.NATIONAL);
    }
    
    private String format(PhoneNumberFormat format) {
        if (this.number != null && this.regionCode != null) {
            try {
                PhoneNumber phoneNumber = PHONE_UTIL.parse(this.number, this.regionCode);
                if (PHONE_UTIL.isValidNumber(phoneNumber)) {            
                    return PHONE_UTIL.format(phoneNumber, format);
                }
            } catch (Exception e) {
            }
        }
        return number;
    }
}
