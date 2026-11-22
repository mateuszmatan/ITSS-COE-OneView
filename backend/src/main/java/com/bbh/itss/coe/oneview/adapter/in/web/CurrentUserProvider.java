package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.domain.user.UserProfile;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserProvider {

    public UserProfile getCurrentUser() {
        return UserProfile.defaultAdmin();
    }
}
