package com.thughari.jobtrackerpro.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesBadRequestExceptions() {
        var response = handler.handleBadRequest(new IllegalArgumentException("bad"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("bad", response.getBody().getMessage());
    }

    @Test
    void handlesNotFound() {
        var response = handler.handleNotFound(new ResourceNotFoundException("missing"));
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("missing", response.getBody().getMessage());
    }

    @Test
    void handlesPayloadTooLarge() {
        var response = handler.handleMaxSizeException(new MaxUploadSizeExceededException(10));
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertTrue(response.getBody().getMessage().contains("File size exceeds"));
    }

    @Test
    void handlesFallbackException() {
        var response = handler.handleGeneralException(new RuntimeException("oops"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("An unexpected error occurred.", response.getBody().getMessage());
    }
}
