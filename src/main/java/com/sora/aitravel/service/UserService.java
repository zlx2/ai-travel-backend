package com.sora.aitravel.service;

import com.sora.aitravel.dto.request.UpdateUserProfileRequest;
import com.sora.aitravel.dto.response.UserInfoResponse;

public interface UserService {
    UserInfoResponse getCurrentUser();

    void updateCurrentUser(UpdateUserProfileRequest request);
}
