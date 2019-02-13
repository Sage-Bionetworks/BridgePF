package org.sagebionetworks.bridge.validators;

import static org.sagebionetworks.bridge.BridgeConstants.API_MAXIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.BridgeConstants.API_MINIMUM_PAGE_SIZE;
import static org.sagebionetworks.bridge.TestUtils.assertValidatorMessage;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.models.AccountSummarySearch;

import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.class)
public class AccountSummarySearchValidatorTest {

    private AccountSummarySearchValidator validator;
    
    private AccountSummarySearch.Builder builder;
    
    @Before
    public void before() {
        validator = new AccountSummarySearchValidator(Sets.newHashSet("group1", "group2"));
        builder = new AccountSummarySearch.Builder();
    }
    
    @Test
    public void emptySearchIsValid() {
        Validate.entityThrowingException(validator, AccountSummarySearch.EMPTY_SEARCH);
    }
    
    @Test
    public void fullSearchIsValid() {
        DateTime startTime = DateTime.now().minusDays(2);
        DateTime endTime = DateTime.now();
        builder.withOffsetBy(10);
        builder.withPageSize(100);
        builder.withEmailFilter("email");
        builder.withPhoneFilter("phone");
        builder.withAllOfGroups(Sets.newHashSet("group1"));
        builder.withNoneOfGroups(Sets.newHashSet("group2"));
        builder.withLanguage("en");
        builder.withStartTime(startTime);
        builder.withEndTime(endTime);
        Validate.entityThrowingException(validator, builder.build());
    }

    @Test
    public void offsetCannotBeNegative() {
        builder.withOffsetBy(-1);
        assertValidatorMessage(validator, builder.build(), "offsetBy", "cannot be less than 0");
    }
    
    @Test
    public void pageSizeTooSmall() {
        builder.withPageSize(BridgeConstants.API_MINIMUM_PAGE_SIZE-1);
        assertValidatorMessage(validator, builder.build(), "pageSize",
                "must be from " + API_MINIMUM_PAGE_SIZE + "-" + API_MAXIMUM_PAGE_SIZE + " records");
    }
    
    @Test
    public void pageSizeTooLarge() {
        builder.withPageSize(BridgeConstants.API_MAXIMUM_PAGE_SIZE+1);
        assertValidatorMessage(validator, builder.build(), "pageSize",
                "must be from " + API_MINIMUM_PAGE_SIZE + "-" + API_MAXIMUM_PAGE_SIZE + " records");
    }
    
    @Test
    public void beforeDateCannotBeAfterStartDate() {
        builder.withStartTime(DateTime.now()).withEndTime(DateTime.now().minusHours(1));
        assertValidatorMessage(validator, builder.build(), "AccountSummarySearch", AccountSummarySearchValidator.DATE_RANGE_ERROR);
    }
    
    @Test
    public void allOfGroupsValid() {
        builder.withAllOfGroups(Sets.newHashSet("group1", "badGroup"));
        assertValidatorMessage(validator, builder.build(), "allOfGroups", "'badGroup' is not in enumeration: group2, group1");
    }
    
    @Test
    public void noneOfGroupsValid() { 
        builder.withNoneOfGroups(Sets.newHashSet("group1", "badGroup"));
        assertValidatorMessage(validator, builder.build(), "noneOfGroups", "'badGroup' is not in enumeration: group2, group1");
    }
    
    @Test
    public void allAndNoneOfGroupsCrossed() {
        builder.withAllOfGroups(Sets.newHashSet("group1")).withNoneOfGroups(Sets.newHashSet("group1"));
        assertValidatorMessage(validator, builder.build(), "allOfGroups", "includes these excluded data groups: group1");
    }
}
