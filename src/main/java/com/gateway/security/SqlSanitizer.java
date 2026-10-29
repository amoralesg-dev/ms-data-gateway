package com.gateway.security;

import java.util.regex.Pattern;

/**
 * Validador estricto para consultas SQL administrativas libres.
 * 
 * Reglas:
 * 1. Solo permite sentencias que comiencen con SELECT (o WITH ... SELECT).
 * 2. Bloquea palabras clave de modificación y DDL (INSERT, UPDATE, DELETE, DROP, ALTER, TRUNCATE, etc.).
 * 3. Bloquea múltiples sentencias separadas por punto y coma ';'.
 * 4. Bloquea comentarios sospechosos '--', '/*', '* /'.
 */
public final class SqlSanitizer {

    private static final Pattern MULTIPLE_STATEMENTS = Pattern.compile(";");
    private static final Pattern SQL_COMMENTS = Pattern.compile("(--|/\\*|\\*/)");
    
    // Palabras clave no permitidas
    private static final Pattern BLOCKED_KEYWORDS = Pattern.compile(
        "\\b(INSERT|UPDATE|DELETE|MERGE|TRUNCATE|DROP|ALTER|CREATE|RENAME|GRANT|REVOKE|EXEC|EXECUTE|CALL)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Debe comenzar con SELECT o WITH (para CTEs de solo lectura)
    private static final Pattern VALID_SELECT_START = Pattern.compile(
        "^\\s*(SELECT|WITH)\\b",
        Pattern.CASE_INSENSITIVE
    );

    private SqlSanitizer() {}

    /**
     * Valida que una consulta SQL sea exclusivamente de lectura y segura.
     * @param sql Consulta enviada
     * @throws SecurityException si incumple alguna restricción
     */
    public static void validateReadOnlySelect(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("La consulta SQL no puede estar vacía");
        }

        String trimmed = sql.trim();

        // 1. Bloquear comentarios sospechosos que puedan ofuscar sentencias
        if (SQL_COMMENTS.matcher(trimmed).find()) {
            throw new SecurityException("Comentarios SQL ('--', '/*') no están permitidos en el modo administrativo.");
        }

        // 2. Bloquear múltiples sentencias (evitar inyecciones con ';')
        if (MULTIPLE_STATEMENTS.matcher(trimmed).find()) {
            throw new SecurityException("Múltiples sentencias SQL separadas por ';' no están permitidas.");
        }

        // 3. Validar que no contenga palabras clave bloqueadas
        var matcher = BLOCKED_KEYWORDS.matcher(trimmed);
        if (matcher.find()) {
            throw new SecurityException("Operación no autorizada. Palabra clave prohibida detectada: " + matcher.group().toUpperCase());
        }

        // 4. Validar que comience con SELECT o WITH
        if (!VALID_SELECT_START.matcher(trimmed).find()) {
            throw new SecurityException("Solo se permiten consultas de tipo SELECT en este endpoint.");
        }
    }
}
