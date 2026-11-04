package com.exskylab.koala.core.utilities.storage;

import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.StorageProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StorageKeysTest {

    private static final String CDN = "https://cdn.koala.test";
    private static final String API = "http://localhost:8080";

    private StorageUrlResolver resolver() {
        StorageProperties storage = new StorageProperties();
        storage.setPublicUrl(CDN);
        return new StorageUrlResolver(storage, new AppProperties(API, "http://localhost:3000"));
    }

    @Test
    void eachFolderTypeGetsItsOwnPrefix() {
        assertTrue(StorageKeys.newKey(FolderType.IMAGE, "a.png").startsWith("images/"));
        assertTrue(StorageKeys.newKey(FolderType.PUBLIC_FILE, "a.pdf").startsWith("public/"));
        assertTrue(StorageKeys.newKey(FolderType.FILE, "a.pdf").startsWith("files/"));
    }

    @Test
    void theKeyKeepsTheExtensionButNotTheOriginalName() {
        String key = StorageKeys.newKey(FolderType.FILE, "Özgeçmişim Son Hâli.PDF");

        assertTrue(key.endsWith(".pdf"));
        assertFalse(key.toLowerCase().contains("hali"), "orijinal ad anahtara sızmamalı");
    }

    @Test
    void aFileNameWithoutAnExtensionYieldsAKeyWithoutOne() {
        assertEquals("", StorageKeys.extensionOf("dosya"));
        assertEquals("", StorageKeys.extensionOf("dosya."));
        assertEquals("", StorageKeys.extensionOf(null));
    }

    @Test
    void aDisguisedExtensionIsRejected() {
        assertEquals("", StorageKeys.extensionOf("kotu.pdf%00exe"));
        assertEquals("", StorageKeys.extensionOf("kotu.thisisaverylongextension"));
    }

    @Test
    void turkishCharactersAreFoldedWhenSanitising() {
        assertEquals("Ozgecmisim_Son_Hali.pdf", StorageKeys.sanitize("Özgeçmişim Son Hâli.pdf"));
    }

    @Test
    void aPathTraversalAttemptCollapsesToUnderscores() {
        String sanitized = StorageKeys.sanitize("../../etc/passwd");

        assertFalse(sanitized.contains(".."));
        assertFalse(sanitized.contains("/"));
    }

    @Test
    void imagesResolveToTheCdnAndPrivateFilesToTheApi() {
        StorageUrlResolver resolver = resolver();

        assertEquals(CDN + "/images/a.png", resolver.urlFor("images/a.png"));
        assertEquals(API + "/uploads/files/a.pdf", resolver.urlFor("files/a.pdf"));
        assertEquals(API + "/uploads/public/a.pdf", resolver.urlFor("public/a.pdf"));
    }

    @Test
    void anAbsoluteUrlIsLeftAlone() {
        assertEquals("https://elsewhere.test/x.png", resolver().urlFor("https://elsewhere.test/x.png"));
    }

    @Test
    void aMissingKeyResolvesToNothing() {
        assertNull(resolver().urlFor(null));
        assertNull(resolver().urlFor("  "));
    }

    @Test
    void aTurkishFileNameSurvivesTheContentDispositionHeader() {
        String header = FileDispositions.inline("Özgeçmiş.pdf");

        assertTrue(header.startsWith("inline; filename=\"Ozgecmis.pdf\""));
        assertTrue(header.contains("filename*=UTF-8''"));
        assertTrue(header.contains("%C3%96"), "UTF-8 kodlu ad da başlıkta bulunmalı");
    }

    @Test
    void onlyKnownPrefixesAreServable() {
        assertTrue(StorageKeys.isKnownPrefix("images/a.png"));
        assertTrue(StorageKeys.isKnownPrefix("files/a.pdf"));
        assertTrue(StorageKeys.isKnownPrefix("public/a.pdf"));
        assertFalse(StorageKeys.isKnownPrefix("../secret"));
        assertFalse(StorageKeys.isKnownPrefix("backups/db.sql"));
    }

    @Test
    void identityDocumentsLiveOutsideEveryServablePrefix() {
        String key = StorageKeys.newKey(FolderType.IDENTITY_DOCUMENT, "kimlik_on_yuz.JPG");

        assertTrue(key.startsWith("identity-documents/"), key);
        assertTrue(key.endsWith(".jpg"), key);
        assertFalse(key.contains("kimlik"), key);
        assertTrue(StorageKeys.isIdentityDocument(key));
        assertFalse(StorageKeys.isKnownPrefix(key));
        assertFalse(StorageKeys.isImage(key));
        assertFalse(StorageKeys.isPrivate(key));
        assertFalse(StorageKeys.isPublicFile(key));
    }

    @Test
    void aKeyThatClimbsOutOfItsPrefixIsNotServable() {
        assertFalse(StorageKeys.isKnownPrefix("images/../identity-documents/a.jpg"));
        assertFalse(StorageKeys.isKnownPrefix("files/./../identity-documents/a.jpg"));
        assertFalse(StorageKeys.isKnownPrefix("images/..\\identity-documents\\a.jpg"));
        assertTrue(StorageKeys.isKnownPrefix("images/foto..eski.jpg"));
    }
}
