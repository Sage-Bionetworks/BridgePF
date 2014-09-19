package org.sagebionetworks.bridge.models.surveys;

import java.util.EnumSet;

import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.validators.Messages;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class DateTimeConstraints extends TimeBasedConstraints {

    private static EnumSet<UIHint> UI_HINTS = EnumSet.of(UIHint.DATETIMEPICKER);
    
    public DateTimeConstraints() {
        setDataType(DataType.DATETIME);
    }
    
    @Override
    @JsonIgnore
    public EnumSet<UIHint> getSupportedHints() {
        return UI_HINTS;
    }
    
    public void validate(Messages messages, SurveyAnswer answer) {
        long time = (Long)answer.getAnswer();
        long now = DateUtils.getCurrentMillisFromEpoch();
        // This sometimes fails in tests and it's not clear why
        if (!allowFuture && time >= now) {
            messages.add("it is not allowed to have a future datetime value (%s >= %s)", time, now);
        }
    }
}
