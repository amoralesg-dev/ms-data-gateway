package com.gateway.qad.mapper;

import com.gateway.qad.envelope.QadSessionContext;

/**
 * Contrato base para mappers de servicios de negocio QAD.
 * Transforma un DTO REST de alto nivel en:
 * 1. Payload XML específico de QAD (ej. <BCreditor>...</BCreditor>)
 * 2. QadSessionContext con los parámetros de sesión requeridos.
 *
 * @param <T> Tipo del DTO de entrada (Request)
 */
public interface QadBusinessMapper<T> {

    /**
     * Identificador del servicio (ej. BCREDITOR)
     */
    String getServiceCode();

    /**
     * Nombre de la operación SOAP (ej. bcreditor)
     */
    String getOperationName();

    /**
     * Construye el contexto de sesión QAD para este request.
     */
    QadSessionContext buildSessionContext(T request);

    /**
     * Transforma el DTO REST a la estructura XML interna esperada por QAD.
     */
    String toXmlPayload(T request);

    /**
     * Retorna la clase de datos DTO que acepta este mapper.
     */
    Class<T> getRequestType();
}
