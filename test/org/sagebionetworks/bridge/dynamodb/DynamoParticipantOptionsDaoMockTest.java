package org.sagebionetworks.bridge.dynamodb;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import org.sagebionetworks.bridge.TestConstants;

public class DynamoParticipantOptionsDaoMockTest {
    @Test
    public void updateNoOptions() {
        // mock mapper
        DynamoDBMapper mockMapper = mock(DynamoDBMapper.class);
        DynamoParticipantOptionsDao optionsDao = new DynamoParticipantOptionsDao();
        optionsDao.setDdbMapper(mockMapper);

        // execute
        optionsDao.setAllOptions(TestConstants.TEST_STUDY, "test-healthcode", ImmutableMap.of());

        // No update done, it didn't change.
        verify(mockMapper, never()).save(any());
    }
}