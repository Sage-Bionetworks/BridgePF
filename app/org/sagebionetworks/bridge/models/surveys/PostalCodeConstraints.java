package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;
import java.util.List;

import org.sagebionetworks.bridge.BridgeConstants;

/**
 * <p>A data type for capturing partial postal code information. Although postal codes can be useful for 
 * locating users, combined with other information (such as gender and birthdate, or a rare disease 
 * condition), they can be identifying. For this reason there is guidance to the client on how this 
 * data can be collected, while preserving anonymity:</p>
 * <ul>
 *  <li>Canada (CA) - The first three characters of a Canadian postal code describe a sorting area of 
 *      about 7,000 households. No specific guidance is given, but collecting the first 3 characters 
 *      has been deemed reasonable. 
 *  </li>
 *  <li>United States (US) - collect only the first three digits of a zip code. Some 3 digit codes 
 *      describe areas of 20,000 or less people; for these exceptional codes, substitute the value 
 *      "000" instead before submitting to the server. If the survey constraints indicate that a 
 *      US zip code is required, the `sparseZipCodePrefixes` array will be included in the constraint, 
 *      with a list of these special partial zip codes. They are provided by the server so they can 
 *      be updated based on new census information without having to update the survey or the client.
 *  <li>
 * </ul>
 * <p>One way to protect the anonymity of the user is to limit the combination of partially identifying 
 * information that is asked for in a survey. For example do no ask for a specific birthdate in a survey 
 * where the information will be submitted to the server. This makes a partial postal code significantly 
 * less useful for reidentification. </p>
 * 
 * @see http://www.ehealthinformation.ca/faq/can-postal-codes-re-identify-individuals/
 * @see https://academic.oup.com/jamia/article/16/2/256/960457
 * @see https://www.ncbi.nlm.nih.gov/pmc/articles/PMC4707567/
 * @see https://www.johndcook.com/blog/2016/06/29/sparsely-populated-zip-codes/
 */
public class PostalCodeConstraints extends Constraints {
    
    private CountryCode countryCode;

    public PostalCodeConstraints() {
        setDataType(DataType.POSTALCODE);
        setSupportedHints(EnumSet.of(UIHint.POSTALCODE));
    }
    
    /**
     * Some 3 digit codes describe areas of 20,000 or less people; for these exceptional codes, which 
     * can more easily be used to reidentify participants, substitute the value "000" instead before 
     * submitting the survey answer to the server. If the survey constraints indicate that a US zip 
     * code is required, the `sparseZipCodePrefixes` array will be included in the constraint, with 
     * a list of these special partial zip codes.
     */
    public List<String> getSparseZipCodePrefixes() {
        return (CountryCode.US == countryCode) ? BridgeConstants.SPARSE_ZIP_CODE_PREFIXES : null;
    }

    public void setSparseZipCodePrefixes(List<String> unused) {
        // noop. This allows deserialization of sparseZipCodePrefixes if this array is sent back 
        // by the client. We ignore it. I cannot find a more elegant way to do this with Jackson. 
    }
    
    public CountryCode getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(CountryCode countryCode) {
        this.countryCode = countryCode;
    }
    
}
