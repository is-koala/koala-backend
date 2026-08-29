package com.exskylab.koala.core.utilities.identity;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

final class BouncyCastleProviders {

    private BouncyCastleProviders() {
    }

    static void register() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }
}
