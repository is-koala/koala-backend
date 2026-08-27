package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.auth.request.*;
import com.exskylab.koala.core.helpers.ClientContext;
import com.exskylab.koala.core.dtos.auth.response.AuthSetPasswordDto;
import com.exskylab.koala.core.dtos.auth.response.TokenResponseDto;

import java.util.UUID;

public interface AuthService {

    void startRegistration(AuthStartRegistrationDto authStartRegistrationDto);

    AuthSetPasswordDto verifyRegistrationToken(AuthVerifyRegistrationTokenDto authVerifyRegistrationTokenDto);

    TokenResponseDto completeRegistration(AuthCompleteRegistrationDto authCompleteRegistrationDto, ClientContext client);

    TokenResponseDto login(AuthLoginDto authLoginDto, ClientContext client);

    TokenResponseDto refreshToken(RefreshTokenDto refreshTokenDto, UUID sessionId, ClientContext client);

    void logout(UUID sessionId);

    void requestPasswordReset(ForgotPasswordDto forgotPasswordDto);

    void resetPassword(ResetPasswordDto resetPasswordDto);

}