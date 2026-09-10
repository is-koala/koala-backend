package com.exskylab.koala.core.configs;

import com.google.api.client.http.ByteArrayContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BoundedGoogleHttpTransportTest {

    @Test
    void overridesCredentialRequestDefaultsWithBoundedTimeoutsAndNoInnerRetry() throws Exception {
        RecordingTransport delegate = new RecordingTransport();
        BoundedGoogleHttpTransport transport = new BoundedGoogleHttpTransport(delegate, 101, 202, 303);

        transport.createRequestFactory(request -> request.setNumberOfRetries(0))
                .buildPostRequest(new GenericUrl("https://oauth2.googleapis.test/token"),
                        ByteArrayContent.fromString("application/x-www-form-urlencoded", "grant_type=x"))
                .execute()
                .disconnect();

        assertEquals(101, delegate.request.connectTimeout);
        assertEquals(202, delegate.request.readTimeout);
        assertEquals(303, delegate.request.writeTimeout);
        assertEquals("grant_type=x", delegate.request.getContentAsString());
        assertEquals(1, delegate.executions);
    }

    private static final class RecordingTransport extends HttpTransport {

        private RecordingRequest request;
        private int executions;

        @Override
        protected LowLevelHttpRequest buildRequest(String method, String url) {
            request = new RecordingRequest(url);
            return request;
        }

        private final class RecordingRequest extends com.google.api.client.testing.http.MockLowLevelHttpRequest {

            private int connectTimeout;
            private int readTimeout;
            private int writeTimeout;

            private RecordingRequest(String url) {
                super(url);
            }

            @Override
            public void setTimeout(int connectTimeout, int readTimeout) {
                this.connectTimeout = connectTimeout;
                this.readTimeout = readTimeout;
            }

            @Override
            public void setWriteTimeout(int writeTimeout) {
                this.writeTimeout = writeTimeout;
            }

            @Override
            public LowLevelHttpResponse execute() throws IOException {
                executions++;
                return new MockLowLevelHttpResponse().setStatusCode(200).setContent("{}");
            }
        }
    }
}
