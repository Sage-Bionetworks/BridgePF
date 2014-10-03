package org.sagebionetworks.bridge.models.surveys;

import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.json.DateTimeJsonDeserializer;
import org.sagebionetworks.bridge.json.DateTimeJsonSerializer;
import org.sagebionetworks.bridge.models.BridgeEntity;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

@BridgeTypeName("SurveyAnswer")
public class SurveyAnswer implements BridgeEntity {

    private String questionGuid;
    private Object answer;
    private long answeredOn;
    private String client;
    private boolean declined;
    
    public String getQuestionGuid() {
        return questionGuid;
    }
    public void setQuestionGuid(String questionGuid) {
        this.questionGuid = questionGuid;
    }
    public Object getAnswer() {
        return answer;
    }
    public void setAnswer(Object answer) {
        this.answer = answer;
    }
    @JsonSerialize(using = DateTimeJsonSerializer.class)
    public long getAnsweredOn() {
        return answeredOn;
    }
    @JsonDeserialize(using = DateTimeJsonDeserializer.class)
    public void setAnsweredOn(long answeredOn) {
        this.answeredOn = answeredOn;
    }
    public String getClient() {
        return client;
    }
    public void setClient(String client) {
        this.client = client;
    }
    public boolean isDeclined() {
        return declined;
    }
    public void setDeclined(boolean declined) {
        this.declined = declined;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((answer == null) ? 0 : answer.hashCode());
        result = prime * result + (int) (answeredOn ^ (answeredOn >>> 32));
        result = prime * result + ((client == null) ? 0 : client.hashCode());
        result = prime * result + (declined ? 1231 : 1237);
        result = prime * result + ((questionGuid == null) ? 0 : questionGuid.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SurveyAnswer other = (SurveyAnswer) obj;
        if (answer == null) {
            if (other.answer != null)
                return false;
        } else if (!answer.equals(other.answer))
            return false;
        if (answeredOn != other.answeredOn)
            return false;
        if (client == null) {
            if (other.client != null)
                return false;
        } else if (!client.equals(other.client))
            return false;
        if (declined != other.declined)
            return false;
        if (questionGuid == null) {
            if (other.questionGuid != null)
                return false;
        } else if (!questionGuid.equals(other.questionGuid))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "SurveyAnswer [questionGuid=" + questionGuid + ", answer=" + answer + ", answeredOn=" + answeredOn
                + ", client=" + client + ", declined=" + declined + "]";
    }
    
}
