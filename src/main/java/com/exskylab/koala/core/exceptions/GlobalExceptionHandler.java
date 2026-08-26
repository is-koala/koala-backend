package com.exskylab.koala.core.exceptions;

import com.exskylab.koala.core.utilities.results.ErrorResult;
import com.exskylab.koala.core.utilities.results.Result;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.security.access.AccessDeniedException;

@ControllerAdvice
public class GlobalExceptionHandler {

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    @ExceptionHandler(KoalaException.class)
    public ResponseEntity<Result> handleKoalaException(KoalaException ex){
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResult(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
     StringBuilder errorMessages = new StringBuilder();

     ex.getBindingResult().getAllErrors().forEach((error) -> {
         String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errorMessages.append(fieldName).append(": ").append(errorMessage).append("; ");
     });

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResult("Validasyon hatası oluştu detaylı bilgi: "+errorMessages, HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result> handleGlobalException(Exception ex) {
        logger.error("Beklenmeyen hata: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResult("Beklenmeyen bir hata oluştu: " + ex.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR));
    }

    //for spring security dawwwgg
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result> handleAccessDeniedException(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErrorResult("Bu işlemi yapmak için yetkiniz yok! " + ex.getMessage(), HttpStatus.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Kimlik doğrulama başarısız: " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Result> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Geçersiz kullanıcı adı ve şifre " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ResponseEntity<Result> handleInsufficientResourcesException(InsufficientAuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Bu işlem için kimlik doğrulaması yapmanız gerekmektedir! " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(DeviceDoesntMatchException.class)
    public ResponseEntity<Result> handleDeviceDoesntMatchException(DeviceDoesntMatchException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Tokende belirtilen sessionun cihazı ile istek yapılan cihaz uyuşmuyor: " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(NotValidAuthorizationHeaderException.class)
    public ResponseEntity<Result> handleNotValidAuthorizationHeaderException(NotValidAuthorizationHeaderException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Geçersiz Authorization header: " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(SessionDoesntMatch.class)
    public ResponseEntity<Result> handleSessionDoesntMatch(SessionDoesntMatch ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Session ID ile kullanıcı ID uyuşmuyor: " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(SignatureException.class)
    public ResponseEntity<Result> handleSignatureException(SignatureException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("Geçersiz veya bozuk JWT.", HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<Result> handleExpiredJwtException(ExpiredJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("JWT'nin süresi dolmuştur. Lütfen tekrar giriş yapın veya refreshtoken ile yeni token alın.", HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(SessionNotFoundException.class)
    public ResponseEntity<Result> handleSessionNotFoundException(SessionNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult(ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Result> handleJwtException(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ErrorResult("JWT işleme hatası: " + ex.getMessage(), HttpStatus.UNAUTHORIZED));
    }

}
