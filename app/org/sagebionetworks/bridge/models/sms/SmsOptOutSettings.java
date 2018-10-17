package org.sagebionetworks.bridge.models.sms;

import java.util.Map;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import org.sagebionetworks.bridge.dynamodb.DynamoSmsOptOutSettings;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;

/** Represents the types of SMS messages a phone number has opted out of (unsubscribed from). */
@BridgeTypeName("SmsOptOutSettings")
@JsonDeserialize(as = DynamoSmsOptOutSettings.class)
public interface SmsOptOutSettings extends BridgeEntity {
    /** Creates an SmsOptOut instance. */
    static SmsOptOutSettings create() {
        return new DynamoSmsOptOutSettings();
    }

    /** The phone number who has opt-outs. */
    String getNumber();

    /** @see #getNumber */
    void setNumber(String number);

    /** True if the phone number has opted out of (unsubscribed from) promotional messages on all studies. */
    boolean getGlobalPromotionalOptOut();

    /** @see #getGlobalPromotionalOptOut */
    void setGlobalPromotionalOptOut(boolean globalPromotionalOptOut);

    /**
     * Promotional opt-outs per study. This takes precedence over global promotional opt-outs. That is, if a user is
     * opted out globally, but opted into study Foo, then the user is opted into study Foo.
     */
    Map<String, Boolean> getPromotionalOptOuts();

    /**
     * Gets the phone number's opt-out status for a specific study, falling back to the global opt-out if the phone
     * number has no study-specific opt-out.
     */
    default boolean getPromotionalOptOutForStudy(String studyId) {
        return getPromotionalOptOuts().getOrDefault(studyId, getGlobalPromotionalOptOut());
    }

    /** Transactional opt-outs per study. */
    Map<String, Boolean> getTransactionalOptOuts();

    /**
     * Gets the phone number's opt-out status for a specific study. If not specified, it defaults to false (not opted
     * out, ie opted in, ie subscribed).
     */
    default boolean getTransactionalOptOutForStudy(String studyId) {
        return getTransactionalOptOuts().getOrDefault(studyId, false);
    }
}
