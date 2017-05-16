package org.sagebionetworks.bridge.okta;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.accounts.AccountSummary;
import org.sagebionetworks.bridge.models.studies.StudyIdentifier;
import org.sagebionetworks.bridge.models.studies.StudyIdentifierImpl;

import com.google.common.collect.Lists;
import com.okta.sdk.clients.UserApiClient;
import com.okta.sdk.framework.PagedResults;
import com.okta.sdk.models.users.User;

@RunWith(MockitoJUnitRunner.class)
public class OktaAccountIteratorTest {

    private static final StudyIdentifier STUDY_ID = new StudyIdentifierImpl("test-study"); 
    
    @Mock
    private UserApiClient mockUserApiClient;
    
    @SuppressWarnings("unchecked")
    @Test
    public void test() throws IOException {
        PagedResults<User> page1 = TestUtils.makeOktaPagedResults(1, false);
        when(mockUserApiClient.getUsersPagedResultsWithLimit(100)).thenReturn(page1);
        
        PagedResults<User> page2 = TestUtils.makeOktaPagedResults(4, false);
        PagedResults<User> page3 = TestUtils.makeOktaPagedResults(7, true);
        when(mockUserApiClient.getUsersPagedResultsAfterCursorWithLimit(any(), eq(100))).thenReturn(page2, page3);
        OktaAccountIterator iter = new OktaAccountIterator(STUDY_ID, mockUserApiClient);
        
        List<AccountSummary> summaries = Lists.newArrayList();
        while(iter.hasNext()) {
            AccountSummary acct = iter.next();
            summaries.add(acct);
        }
        for (int i=0; i < summaries.size(); i++) {
            assertEquals("User #"+(i+1), summaries.get(i).getFirstName());
        }
    }
}
