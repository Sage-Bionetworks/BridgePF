package org.sagebionetworks.bridge.dao;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.LinkedHashSet;
import java.util.Set;

import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.TextNode;

public enum ParticipantOption {

    TIME_ZONE(null, "timeZone") {
        public String fromAccount(Account account) {
            return DateUtils.timeZoneToOffsetString(account.getTimeZone());
        }
        public String fromParticipant(StudyParticipant participant) {
            return DateUtils.timeZoneToOffsetString(participant.getTimeZone());
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            try {
                DateTimeZone zone = DateUtils.parseZoneFromOffsetString(node.asText());
                return DateUtils.timeZoneToOffsetString(zone);
            } catch(IllegalArgumentException e) {
                throw new BadRequestException("timeZone is an invalid time zone offset");
            }
        }        
    },
    SHARING_SCOPE(SharingScope.NO_SHARING.name(), "sharingScope") {
        public String fromAccount(Account account) {
            return (account.getSharingScope() == null) ? null : account.getSharingScope().name();
        }
        public String fromParticipant(StudyParticipant participant) {
            return (participant.getSharingScope() == null) ? null : participant.getSharingScope().name();
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            // Both incorrect enum names and JsonNode type problems end up throwing IllegalArgumentException
            try {
                return SharingScope.valueOf(node.asText().toUpperCase()).name();    
            } catch(IllegalArgumentException e) {
                throw new BadRequestException("sharingScope is an invalid type");
            }
        }
    },
    EMAIL_NOTIFICATIONS(Boolean.TRUE.toString(), "notifyByEmail") {
        public String fromAccount(Account account) {
            Boolean bool = account.getNotifyByEmail();
            return (bool == null) ? getDefaultValue() : Boolean.toString(bool);
        }
        public String fromParticipant(StudyParticipant participant) {
            Boolean bool = participant.isNotifyByEmail();
            return (bool == null) ? getDefaultValue() : Boolean.toString(bool);
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            checkTypeAndThrow(node, "notifyByEmail", BooleanNode.class);
            return Boolean.toString(node.asBoolean());
        }
    },
    EXTERNAL_IDENTIFIER(null, "externalId") {
        public String fromAccount(Account account) {
            return account.getExternalId();
        }
        public String fromParticipant(StudyParticipant participant) {
            return participant.getExternalId();
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            checkTypeAndThrow(node, "externalId", TextNode.class);
            return node.asText();
        }
    },
    DATA_GROUPS(null, "dataGroups") {
        public String fromAccount(Account account) {
            return BridgeUtils.setToCommaList(account.getDataGroups());
        }
        public String fromParticipant(StudyParticipant participant) {
            return BridgeUtils.setToCommaList(participant.getDataGroups());
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            checkTypeAndThrow(node, "dataGroups", ArrayNode.class);
            return arrayNodeToOrderedString(super.getFieldName(), node);
        }
    },
    LANGUAGES(null, "languages") {
        public String fromAccount(Account account) {
            return BridgeUtils.setToCommaList(account.getLanguages());
        }
        public String fromParticipant(StudyParticipant participant) {
            return BridgeUtils.setToCommaList(participant.getLanguages());
        }
        public String deserialize(JsonNode node) {
            checkNotNull(node);
            checkTypeAndThrow(node, "language", ArrayNode.class);
            return arrayNodeToOrderedString(super.getFieldName(), node);
        }
    };
    
    private static void checkTypeAndThrow(JsonNode node, String fieldName, Class<? extends JsonNode> type) {
        if (!node.getClass().isAssignableFrom(type)) {
            throw new BadRequestException(fieldName + " is an invalid type");
        }
    }
    
    private static String arrayNodeToOrderedString(String fieldName, JsonNode node) {
        Set<String> results = new LinkedHashSet<>();
        ArrayNode array = (ArrayNode)node;
        for (int i = 0; i < array.size(); i++) {
            checkTypeAndThrow(array.get(i), fieldName+" element", TextNode.class);
            results.add(array.get(i).asText());
        }
        return BridgeUtils.setToCommaList(results);
    }

    private final String defaultValue;
    private final String fieldName;
    
    ParticipantOption(String defaultValue, String fieldName) {
        this.defaultValue = defaultValue;
        this.fieldName = fieldName;
    }
    
    public String getFieldName() {
        return fieldName;
    }
    
    public String getDefaultValue() {
        return defaultValue;
    }
    
    public abstract String fromAccount(Account account);
    
    public abstract String fromParticipant(StudyParticipant participant);
    
    public abstract String deserialize(JsonNode node);

    public enum SharingScope {
        /**
         * Don't export data generated by this participant.
         */
        NO_SHARING("Not Sharing"),
        /**
         * Only export participant's data to a data set for the original study researchers and their affiliated research
         * partners.
         */
        SPONSORS_AND_PARTNERS("Sponsors and Partners Only"),
        /**
         * Export participant's data to a data set that can be used by any researcher who qualifies given the governance
         * qualifications of this data set.
         */
        ALL_QUALIFIED_RESEARCHERS("All Qualified Researchers");
        
        private final String label;
        
        SharingScope(String label) {
            this.label = label;
        }
        public String getLabel() {
            return label;
        }
    }
   
}
