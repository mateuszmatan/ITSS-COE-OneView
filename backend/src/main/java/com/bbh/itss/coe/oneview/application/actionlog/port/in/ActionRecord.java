package com.bbh.itss.coe.oneview.application.actionlog.port.in;

import com.bbh.itss.coe.oneview.domain.user.UserProfile;
import lombok.With;

/**
 * A change a user made, as the Action History shows it. The department (business service) and the product
 * (application) are the ones the changed resource belongs to, when it belongs to one.
 */
@With
public record ActionRecord(UserProfile user, String actionType, String resourceType, String resourceName,
                           Long departmentId, Long productId, String details, boolean success) {

    public static ActionRecord of(String actionType, String resourceType, String resourceName, Long departmentId) {
        return new ActionRecord(null, actionType, resourceType, resourceName, departmentId, null, null, true);
    }

    public ActionRecord done(String details) {
        return withDetails(details).withSuccess(true);
    }

    public ActionRecord failed(String reason) {
        return withDetails(reason).withSuccess(false);
    }
}
