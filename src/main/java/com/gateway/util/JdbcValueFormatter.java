package com.gateway.util;

import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utilidad unificada para formatear tipos SQL / temporales a ISO-8601.
 * Asegura consistencia de salida en cualquier motor de base de datos.
 */
public final class JdbcValueFormatter {

    private static final DateTimeFormatter ISO_DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter ISO_INSTANT_FORMATTER = DateTimeFormatter.ISO_INSTANT;

    private JdbcValueFormatter() {}

    /**
     * Convierte el valor devuelto por ResultSet.getObject() a su representación canónica.
     * En particular, fechas, horas y timestamps se convierten a String ISO-8601.
     */
    public static Object formatValue(Object val) {
        if (val == null) {
            return null;
        }

        if (val instanceof Timestamp ts) {
            // "2026-09-03T14:30:15.123Z"
            return ISO_INSTANT_FORMATTER.format(ts.toInstant());
        }

        if (val instanceof Date d) {
            // "2026-09-03"
            return d.toLocalDate().format(ISO_DATE_FORMATTER);
        }

        if (val instanceof Time t) {
            // "14:30:15"
            return t.toLocalTime().format(ISO_TIME_FORMATTER);
        }

        if (val instanceof Instant inst) {
            return ISO_INSTANT_FORMATTER.format(inst);
        }

        if (val instanceof OffsetDateTime odt) {
            return odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        }

        return val;
    }
}
