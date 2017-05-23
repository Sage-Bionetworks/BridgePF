package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkArgument;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import javax.annotation.Resource;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ExportTimeDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ExportTime;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.validators.Validate;

@Component
public class DynamoExportTimeDao implements ExportTimeDao {
    private DynamoDBMapper mapper;

    @Resource(name = "exportTimeDdbMapper")
    final void setDdbMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }

    @Override public ExportTime getExportTime(String studyId) {
        checkArgument(isNotBlank(studyId), Validate.CANNOT_BE_BLANK, "studyId");
        ExportTime exportTime = ExportTime.create();
        exportTime.setStudyId(studyId);
        exportTime = mapper.load(exportTime);
        if (exportTime == null) {
            throw new EntityNotFoundException(Study.class, "Study '" + studyId + "' not found in ExportTime table.");
        }
        return exportTime;
    }

    @Override public void deleteStudyInfo(String studyId) {
        checkArgument(isNotBlank(studyId), Validate.CANNOT_BE_BLANK, "studyId");
        ExportTime exportTime = ExportTime.create();
        exportTime.setStudyId(studyId);
        exportTime = mapper.load(exportTime);
        // delete only if it exists in ddb
        if (exportTime != null) {
            mapper.delete(exportTime);
        }
    }
}
