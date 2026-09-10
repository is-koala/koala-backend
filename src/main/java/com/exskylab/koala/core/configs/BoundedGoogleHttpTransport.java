package com.exskylab.koala.core.configs;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.util.StreamingContent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BoundedGoogleHttpTransport extends HttpTransport {

    private final HttpTransport delegate;
    private final int connectTimeout;
    private final int readTimeout;
    private final int writeTimeout;

    BoundedGoogleHttpTransport(int connectTimeout, int readTimeout, int writeTimeout) {
        this(new NetHttpTransport(), connectTimeout, readTimeout, writeTimeout);
    }

    BoundedGoogleHttpTransport(HttpTransport delegate, int connectTimeout, int readTimeout, int writeTimeout) {
        this.delegate = delegate;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.writeTimeout = writeTimeout;
    }

    @Override
    public boolean supportsMethod(String method) throws IOException {
        return delegate.supportsMethod(method);
    }

    @Override
    protected LowLevelHttpRequest buildRequest(String method, String url) {
        return new BoundedRequest(method, url);
    }

    @Override
    public void shutdown() throws IOException {
        delegate.shutdown();
    }

    private final class BoundedRequest extends LowLevelHttpRequest {

        private final String method;
        private final String url;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();

        private BoundedRequest(String method, String url) {
            this.method = method;
            this.url = url;
        }

        @Override
        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name, ignored -> new ArrayList<>()).add(value);
        }

        @Override
        public void setTimeout(int ignoredConnectTimeout, int ignoredReadTimeout) {
        }

        @Override
        public void setWriteTimeout(int ignoredWriteTimeout) {
        }

        @Override
        public LowLevelHttpResponse execute() throws IOException {
            HttpRequest request = delegate.createRequestFactory().buildRequest(
                    method, new GenericUrl(url), content());
            headers.forEach((name, values) -> request.getHeaders().set(name, values));
            request.setConnectTimeout(connectTimeout);
            request.setReadTimeout(readTimeout);
            request.setWriteTimeout(writeTimeout);
            request.setNumberOfRetries(0);
            request.setRetryOnExecuteIOException(false);
            request.setFollowRedirects(false);
            request.setThrowExceptionOnExecuteError(false);
            request.setLoggingEnabled(false);
            request.setCurlLoggingEnabled(false);
            request.setResponseReturnRawInputStream(true);
            return new BoundedResponse(request.execute());
        }

        private HttpContent content() {
            StreamingContent streamingContent = getStreamingContent();
            if (streamingContent == null) {
                return null;
            }
            return new HttpContent() {
                @Override
                public long getLength() {
                    return getContentLength();
                }

                @Override
                public String getType() {
                    return getContentType();
                }

                @Override
                public boolean retrySupported() {
                    return false;
                }

                @Override
                public void writeTo(OutputStream outputStream) throws IOException {
                    streamingContent.writeTo(outputStream);
                }
            };
        }
    }

    private static final class BoundedResponse extends LowLevelHttpResponse {

        private final HttpResponse response;
        private final List<Map.Entry<String, String>> headers;

        private BoundedResponse(HttpResponse response) {
            this.response = response;
            this.headers = response.getHeaders().entrySet().stream()
                    .flatMap(entry -> response.getHeaders().getHeaderStringValues(entry.getKey()).stream()
                            .map(value -> Map.entry(entry.getKey(), value)))
                    .toList();
        }

        @Override
        public InputStream getContent() throws IOException {
            return response.getContent();
        }

        @Override
        public String getContentEncoding() {
            return response.getContentEncoding();
        }

        @Override
        public long getContentLength() {
            Long length = response.getHeaders().getContentLength();
            return length == null ? -1 : length;
        }

        @Override
        public String getContentType() {
            return response.getContentType();
        }

        @Override
        public String getStatusLine() {
            return null;
        }

        @Override
        public int getStatusCode() {
            return response.getStatusCode();
        }

        @Override
        public String getReasonPhrase() {
            return response.getStatusMessage();
        }

        @Override
        public int getHeaderCount() {
            return headers.size();
        }

        @Override
        public String getHeaderName(int index) {
            return headers.get(index).getKey();
        }

        @Override
        public String getHeaderValue(int index) {
            return headers.get(index).getValue();
        }

        @Override
        public void disconnect() throws IOException {
            response.disconnect();
        }
    }
}
