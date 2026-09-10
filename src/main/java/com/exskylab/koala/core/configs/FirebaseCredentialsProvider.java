package com.exskylab.koala.core.configs;

import com.google.auth.oauth2.GoogleCredentials;

import java.io.IOException;

@FunctionalInterface
public interface FirebaseCredentialsProvider {

    GoogleCredentials load() throws IOException;
}
