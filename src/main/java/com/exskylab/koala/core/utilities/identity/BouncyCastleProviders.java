package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

public final class BouncyCastleProviders {

    private BouncyCastleProviders() {
    }

    public static void register() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
