package com.exskylab.koala.business.concretes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.business.abstracts.JobApplicationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.ImageMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.mappers.ImageMapper;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.ImageDao;
import com.exskylab.koala.dataAccess.MediaDao;
import com.exskylab.koala.entities.File;
import com.exskylab.koala.entities.Image;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MediaUploadRollbackTest {

    private static final String NEW_IMAGE_KEY = "images/47f7b083-32c6-4e09-a851-1f90f1e66c34.png";
    private static final String NEW_CV_KEY = "files/91724f20-d7af-4ae8-8a50-585fa2042f0d.pdf";
    private static final String OLD_CV_KEY = "files/31dbd99a-0e53-4fe9-b626-649082651221.pdf";

    private ImageDao imageDao;
    private MediaDao mediaDao;
    private UserService userService;
    private StorageService imageStorageService;
    private StorageService cvStorageService;
    private ImageManager imageManager;
    private CvManager cvManager;
    private User currentUser;
    private Logger rootLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        imageDao = mock(ImageDao.class);
        mediaDao = mock(MediaDao.class);
        userService = mock(UserService.class);
        imageStorageService = mock(StorageService.class);
        cvStorageService = mock(StorageService.class);

        imageManager = new ImageManager(imageDao, imageStorageService, mock(SecurityService.class),
                mock(ImageMapper.class), mock(UploadValidator.class));

        currentUser = new User();
        currentUser.setId(UUID.randomUUID());
        File oldCv = new File();
        oldCv.setFileUrl(OLD_CV_KEY);
        currentUser.setCv(oldCv);

        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAuthenticatedUser()).thenReturn(currentUser);
        cvManager = new CvManager(mediaDao, userService, cvStorageService, securityService,
                mock(MediaUrlResolver.class), mock(UploadValidator.class), mock(JobApplicationService.class));

        when(imageStorageService.uploadFile(any(), any(), any(), eq(FolderType.IMAGE)))
                .thenReturn(NEW_IMAGE_KEY);
        when(cvStorageService.uploadFile(any(), any(), any(), eq(FolderType.FILE)))
                .thenReturn(NEW_CV_KEY);
        when(imageDao.save(any(Image.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(mediaDao.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void imageUploadEntryPointsCreateTransactions() throws NoSuchMethodException {
        AnnotationTransactionAttributeSource attributes = new AnnotationTransactionAttributeSource();

        assertNotNull(attributes.getTransactionAttribute(
                ImageManager.class.getMethod("uploadImage", org.springframework.web.multipart.MultipartFile.class),
                ImageManager.class));
        assertNotNull(attributes.getTransactionAttribute(
                ImageManager.class.getMethod("uploadImageDto", org.springframework.web.multipart.MultipartFile.class),
                ImageManager.class));
    }

    @Test
    void anImageWrittenBeforeRollbackIsRemoved() throws IOException {
        beginTransactionSynchronization();

        imageManager.uploadImage(png());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(imageStorageService).deleteFile(NEW_IMAGE_KEY);
    }

    @Test
    void aCommittedImageKeepsItsObject() throws IOException {
        beginTransactionSynchronization();

        imageManager.uploadImage(png());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(imageStorageService, never()).deleteFile(any());
    }

    @Test
    void anImageWithAnUnknownTransactionOutcomeKeepsItsObject() throws IOException {
        beginTransactionSynchronization();

        imageManager.uploadImage(png());
        completeTransaction(TransactionSynchronization.STATUS_UNKNOWN);

        verify(imageStorageService, never()).deleteFile(any());
    }

    @Test
    void anImageCleanupFailureDoesNotMaskThePersistenceFailureAndIsLogged() throws IOException {
        IllegalStateException persistenceFailure = new IllegalStateException("image row failed");
        doThrow(persistenceFailure).when(imageDao).save(any(Image.class));
        doThrow(new IllegalStateException("image cleanup failed"))
                .when(imageStorageService).deleteFile(NEW_IMAGE_KEY);
        beginTransactionSynchronization();

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> imageManager.uploadImage(png()));

        assertEquals(ImageMessages.IMAGE_UPLOAD_ERROR, thrown.getMessage());
        assertDoesNotThrow(() -> completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertCleanupFailureWasLogged("image cleanup failed");
    }

    @Test
    void aCvWrittenBeforeRollbackIsRemovedWithoutDeletingThePreviousCv() {
        beginTransactionSynchronization();

        cvManager.uploadCv(pdf());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(cvStorageService).deleteFile(NEW_CV_KEY);
        verify(cvStorageService, never()).deleteFile(OLD_CV_KEY);
    }

    @Test
    void aCommittedCvKeepsItsObject() {
        beginTransactionSynchronization();

        cvManager.uploadCv(pdf());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);

        verify(cvStorageService, never()).deleteFile(any());
    }

    @Test
    void aCvWithAnUnknownTransactionOutcomeKeepsItsObject() {
        beginTransactionSynchronization();

        cvManager.uploadCv(pdf());
        completeTransaction(TransactionSynchronization.STATUS_UNKNOWN);

        verify(cvStorageService, never()).deleteFile(any());
    }

    @Test
    void aCvCleanupFailureDoesNotMaskTheRelationshipFailureAndIsLogged() {
        IllegalStateException relationshipFailure = new IllegalStateException("cv relation failed");
        doThrow(relationshipFailure).when(userService).save(any(User.class));
        doThrow(new IllegalStateException("cv cleanup failed"))
                .when(cvStorageService).deleteFile(NEW_CV_KEY);
        beginTransactionSynchronization();

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> cvManager.uploadCv(pdf()));

        assertSame(relationshipFailure, thrown);
        assertDoesNotThrow(() -> completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertCleanupFailureWasLogged("cv cleanup failed");
    }

    private void assertCleanupFailureWasLogged(String message) {
        assertTrue(logAppender.list.stream().anyMatch(event ->
                event.getLevel() == Level.ERROR
                        && event.getFormattedMessage().contains("Orphaned storage object could not be removed")
                        && event.getThrowableProxy() != null
                        && message.equals(event.getThrowableProxy().getMessage())));
    }

    private static void beginTransactionSynchronization() {
        TransactionSynchronizationManager.initSynchronization();
    }

    private static void completeTransaction(int status) {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(status));
    }

    private static MockMultipartFile png() throws IOException {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return new MockMultipartFile("image", "profile.png", "image/png", output.toByteArray());
    }

    private static MockMultipartFile pdf() {
        return new MockMultipartFile("document", "cv.pdf", "application/pdf",
                "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8));
    }
}
