package com.cloud.app.model;

/**
 * Estado de la generacion de la copia desenfocada de una imagen sensible.
 * Las imagenes no sensibles no tienen estado (null).
 */
public enum ProcessingStatus {
    PENDING,
    DONE,
    FAILED
}
