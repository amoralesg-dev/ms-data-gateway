package com.gateway.qad.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Solicitud simplificada para la creación o mantenimiento de Acreedor/Proveedor (BCreditor)")
public class BCreditorRequest {

    @NotBlank(message = "creditorCode es requerido")
    @Schema(description = "Código del acreedor / proveedor en QAD", example = "60000011", requiredMode = Schema.RequiredMode.REQUIRED)
    private String creditorCode;

    @Schema(description = "Código de relación de negocio (normalmente igual al creditorCode)", example = "60000011")
    private String businessRelationCode;

    @NotBlank(message = "businessRelationName1 es requerido")
    @Schema(description = "Razón social o nombre comercial del proveedor", example = "HUGO CESAR MUNTANE RODRIGUEZ", requiredMode = Schema.RequiredMode.REQUIRED)
    private String businessRelationName1;

    @Schema(description = "Código de moneda", example = "MXN", defaultValue = "MXN")
    @Builder.Default
    private String currencyCode = "MXN";

    @Schema(description = "RFC o Tax ID Federal", example = "MURH841115B68")
    private String taxIdFederal;

    @Schema(description = "Tax ID Estatal", example = "MURH841115B68")
    private String taxIdState;

    @Schema(description = "Zona fiscal", example = "MX", defaultValue = "MX")
    @Builder.Default
    private String taxZone = "MX";

    @Schema(description = "Clase fiscal", example = "HON", defaultValue = "HON")
    @Builder.Default
    private String taxClass = "HON";

    @Schema(description = "Uso fiscal", example = "")
    private String taxUsage;

    @Schema(description = "Código de condición de pago normal", example = "30", defaultValue = "30")
    @Builder.Default
    private String paymentConditionCode = "30";

    @Schema(description = "Tipo de condición de pago", example = "NORMAL", defaultValue = "NORMAL")
    @Builder.Default
    private String paymentConditionType = "NORMAL";

    @Schema(description = "Tipo de entrega de IVA", example = "PRODUCT", defaultValue = "PRODUCT")
    @Builder.Default
    private String vatDeliveryType = "PRODUCT";

    @Schema(description = "Nivel de porcentaje de IVA", example = "NONE", defaultValue = "NONE")
    @Builder.Default
    private String vatPercentageLevel = "NONE";

    @Schema(description = "Perfil contable de compras", example = "P_Compras", defaultValue = "P_Compras")
    @Builder.Default
    private String purchaseGLProfileCode = "P_Compras";

    @Schema(description = "Perfil contable de control de inventario", example = "P_2010", defaultValue = "P_2010")
    @Builder.Default
    private String invControlGLProfileCode = "P_2010";

    @Schema(description = "Perfil contable de control CN", example = "P_2010", defaultValue = "P_2010")
    @Builder.Default
    private String cnControlGLProfileCode = "P_2010";

    @Schema(description = "Perfil contable de anticipos", example = "P_2010", defaultValue = "P_2010")
    @Builder.Default
    private String prepayControlGLProfileCode = "P_2010";

    @Schema(description = "Perfil de división", example = "Sub_0620", defaultValue = "Sub_0620")
    @Builder.Default
    private String divisionProfileCode = "Sub_0620";

    @Schema(description = "Código de motivo", example = "INV TO APPROVE", defaultValue = "INV TO APPROVE")
    @Builder.Default
    private String reasonCode = "INV TO APPROVE";

    @Schema(description = "Tipo de compra", example = "B001", defaultValue = "B001")
    @Builder.Default
    private String purchaseTypeCode = "B001";

    @Schema(description = "Indica si el acreedor está activo", example = "true", defaultValue = "true")
    @Builder.Default
    private Boolean active = true;

    @Schema(description = "Acción de negocio específica para tContextInfo (SAVE, CREATE)", example = "SAVE", defaultValue = "SAVE")
    @Builder.Default
    private String action = "SAVE";

    @Schema(description = "Actividad específica para tContextInfo (CREATE, UPDATE)", example = "CREATE")
    private String activityCode;

    // Campos de contexto opcionales para sobreescribir configuración global si es necesario
    @Schema(description = "Dominio QAD (opcional, usa default configurado si se omite)", example = "MXMGT01")
    private String domain;

    @Schema(description = "Entidad QAD (opcional, usa default configurado si se omite)", example = "0109")
    private String entity;

    @Schema(description = "Código de compañía (opcional, usa default configurado si se omite)", example = "0111")
    private String companyCode;
}

