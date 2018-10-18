package org.sagebionetworks.bridge.models.accounts;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;

/**
 * A phone number. Phone is mutable, do not use it as a key in a map.
 */
@Embeddable
public final class Phone {
    
    public static final boolean isValid(Phone phone) {
        checkNotNull(phone);
        try {
            PhoneNumber phoneNumber = PHONE_UTIL.parse(phone.getNumber(), phone.getRegionCode());
            return PHONE_UTIL.isValidNumber(phoneNumber);
        } catch (NumberParseException e) {
        }
        return false;
    }
    
    private static final PhoneNumberUtil PHONE_UTIL = PhoneNumberUtil.getInstance();
    private String number;
    private String regionCode;
    
    // For Hibernate. 
    Phone() {
    }
    
    public Phone(String number, String regionCode) {
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
            } catch (NumberParseException e) {
            }
        }
        return number;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNumber(), regionCode);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Phone other = (Phone) obj;
        return Objects.equals(getNumber(), other.getNumber()) && Objects.equals(regionCode, other.regionCode);
    }

    @Override
    public String toString() {
        return "Phone [regionCode=" + regionCode + ", number=" + number + "]";
    }
}
