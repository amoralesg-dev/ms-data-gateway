package com.gateway.dialect;

/**
 * Interfaz de dialecto que encapsula particularidades sintácticas y de paginación
 * de cada motor de base de datos. Permite que el gateway sea agnóstico a la tecnología subyacente.
 */
public interface DatabaseDialect {

    /**
     * Tipo de motor asociado a este dialecto.
     */
    EngineType getEngineType();

    /**
     * Aplica la sintaxis de paginación nativa al SQL si el motor lo soporta.
     * @param sql Consulta SQL original
     * @param page Número de página (1-based)
     * @param pageSize Tamaño de página
     * @return Consulta con cláusulas de paginación
     */
    String applyPagination(String sql, int page, int pageSize);

    /**
     * Query ligera para validar salud y conexión del pool.
     */
    String getValidationQuery();

    /**
     * Indica si el motor soporta paginación SQL nativa (ej. LIMIT o OFFSET/FETCH)
     * o si requiere corte en memoria (como OpenEdge en versiones heredadas).
     */
    boolean supportsNativePagination();
}
