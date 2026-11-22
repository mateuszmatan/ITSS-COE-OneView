package com.bbh.itss.coe.oneview.adapter.in.web;

import com.bbh.itss.coe.oneview.domain.user.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final CurrentUserProvider users;

    @GetMapping("/api/user/me")
    public UserProfile me() {
        return users.getCurrentUser();
    }
}
