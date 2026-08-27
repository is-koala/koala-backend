package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.auth.request.*;
import com.exskylab.koala.core.dtos.auth.response.AuthSetPasswordDto;
import com.exskylab.koala.core.dtos.auth.response.TokenResponseDto;

import java.util.UUID;

public interface AuthService {

    void startRegistration(AuthStartRegistrationDto authStartRegistrationDto);

    AuthSetPasswordDto verifyRegistrationToken(AuthVerifyRegistrationTokenDto authVerifyRegistrationTokenDto);

    TokenResponseDto completeRegistration(AuthCompleteRegistrationDto authCompleteRegistrationDto, String ipAddress, String userAgent);

    TokenResponseDto login(AuthLoginDto authLoginDto, String ipAddress, String userAgent);

    TokenResponseDto refreshToken(RefreshTokenDto refreshTokenDto, UUID sessionId, String ipAddress, String userAgent);

    void logout(UUID sessionId);

    void requestPasswordReset(ForgotPasswordDto forgotPasswordDto);

    void resetPassword(ResetPasswordDto resetPasswordDto);

}