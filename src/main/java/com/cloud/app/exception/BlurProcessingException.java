package com.cloud.app.exception;

/**
 * La copia desenfocada no se pudo generar (fallo al invocar Lambda o respuesta de error).
 * No llega al cliente: ImageStorageService la convierte en processingStatus=FAILED.
 */
public class BlurProcessingException extends RuntimeException {

    public BlurProcessingException(String message) {
        super(message);
    }

    public BlurProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
