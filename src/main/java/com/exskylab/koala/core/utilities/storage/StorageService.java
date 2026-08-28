package com.exskylab.koala.core.utilities.storage;

public interface StorageService {

    String uploadFile(byte[] fileBytes, String originalFileName, String contentType, FolderType folderType);

    void deleteFile(String key);

    String temporaryUrl(String key);

    byte[] readFile(String key);

    boolean supportsSignedUrls();
}
