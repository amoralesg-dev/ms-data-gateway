package com.gateway.qad.mapper.impl;

import com.gateway.qad.config.QadQxtendProperties;
import com.gateway.qad.dto.BCreditorRequest;
import com.gateway.qad.envelope.QadSessionContext;
import com.gateway.qad.mapper.QadBusinessMapper;
import com.gateway.qad.util.XmlDocumentHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import static com.gateway.qad.envelope.QadSoapEnvelopeBuilder.NS_QSVC;
import static com.gateway.qad.util.XmlDocumentHelper.appendElementNS;

/**
 * Mapper oficial para el servicio BCreditor de QAD QXtend.
 * Construye el árbol XML mediante DOM (org.w3c.dom) asegurando serialización robusta
 * y libre de inyecciones o errores de concatenación de texto.
 */
@Component
@RequiredArgsConstructor
public class BCreditorMapper implements QadBusinessMapper<BCreditorRequest> {

    public static final String SERVICE_CODE = "BCREDITOR";
    public static final String OPERATION_NAME = "bcreditor";

    private final QadQxtendProperties properties;

    @Override
    public String getServiceCode() {
        return SERVICE_CODE;
    }

    @Override
    public String getOperationName() {
        return OPERATION_NAME;
    }

    @Override
    public Class<BCreditorRequest> getRequestType() {
        return BCreditorRequest.class;
    }

    @Override
    public QadSessionContext buildSessionContext(BCreditorRequest request) {
        return QadSessionContext.builder()
                .domain(request.getDomain() != null ? request.getDomain() : properties.getDefaultDomain())
                .version(properties.getDefaultVersion())
                .entity(request.getEntity() != null ? request.getEntity() : properties.getDefaultEntity())
                .action(request.getAction() != null ? request.getAction() : properties.getDefaultAction())
                .scopeTransaction("false")
                .mnemonicsRaw("false")
                .build();
    }

    @Override
    public String toXmlPayload(BCreditorRequest req) {
        String companyCode = req.getCompanyCode() != null ? req.getCompanyCode() : properties.getDefaultCompanyCode();
        String action = req.getAction() != null ? req.getAction() : properties.getDefaultAction();
        String activityCode = req.getActivityCode() != null ? req.getActivityCode() : "CREATE";

        String businessRelationCode = req.getBusinessRelationCode() != null && !req.getBusinessRelationCode().isBlank()
                ? req.getBusinessRelationCode()
                : req.getCreditorCode();

        Document doc = XmlDocumentHelper.createDocument();

        Element root = doc.createElementNS(NS_QSVC, "BCreditor");
        doc.appendChild(root);

        // 1. Context Info
        Element contextInfo = appendElementNS(root, NS_QSVC, "tContextInfo", null);
        appendElementNS(contextInfo, NS_QSVC, "tcCompanyCode", companyCode);
        appendElementNS(contextInfo, NS_QSVC, "tcInvolvedCompanyCodes", companyCode);
        appendElementNS(contextInfo, NS_QSVC, "tcAction", action);
        if (activityCode != null && !activityCode.isBlank()) {
            appendElementNS(contextInfo, NS_QSVC, "tcActivityCode", activityCode);
        }
        appendElementNS(contextInfo, NS_QSVC, "tlPartialUpdate", "false");

        // 2. Creditor Data
        Element creditor = appendElementNS(root, NS_QSVC, "tCreditor", null);
        appendElementNS(creditor, NS_QSVC, "CreditorIsActive", String.valueOf(req.getActive() != null ? req.getActive() : true));
        appendElementNS(creditor, NS_QSVC, "CreditorCode", req.getCreditorCode());
        appendElementNS(creditor, NS_QSVC, "VatDeliveryType", req.getVatDeliveryType());
        appendElementNS(creditor, NS_QSVC, "VatPercentageLevel", req.getVatPercentageLevel());
        appendElementNS(creditor, NS_QSVC, "CreditorIsSendRemittance", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsIndividualPaymnt", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsTaxable", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsTaxInCity", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsTaxIncluded", "false");

        if (req.getTaxIdFederal() != null && !req.getTaxIdFederal().isBlank()) {
            appendElementNS(creditor, NS_QSVC, "CreditorTaxIDFederal", req.getTaxIdFederal());
        }
        if (req.getTaxIdState() != null && !req.getTaxIdState().isBlank()) {
            appendElementNS(creditor, NS_QSVC, "CreditorTaxIDState", req.getTaxIdState());
        }

        appendElementNS(creditor, NS_QSVC, "CreditorTaxDeclaration", "0");
        appendElementNS(creditor, NS_QSVC, "CreditorIsTaxReport", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsTaxConfirmed", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsWHT", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorIsBearBankCharge", "false");
        appendElementNS(creditor, NS_QSVC, "CreditorCommentNote", " ");

        appendElementNS(creditor, NS_QSVC, "TxzTaxZone", req.getTaxZone());
        appendElementNS(creditor, NS_QSVC, "TxclTaxCls", req.getTaxClass());
        if (req.getTaxUsage() != null) {
            appendElementNS(creditor, NS_QSVC, "TxuTaxUsage", req.getTaxUsage());
        }

        appendElementNS(creditor, NS_QSVC, "tcNormalPaymentConditionCode", req.getPaymentConditionCode());
        appendElementNS(creditor, NS_QSVC, "tcInvControlGLProfileCode", req.getInvControlGLProfileCode());
        appendElementNS(creditor, NS_QSVC, "tcCnControlGLProfileCode", req.getCnControlGLProfileCode());
        appendElementNS(creditor, NS_QSVC, "tcPrepayControlGLProfileCode", req.getPrepayControlGLProfileCode());
        appendElementNS(creditor, NS_QSVC, "tcDivisionProfileCode", req.getDivisionProfileCode());
        appendElementNS(creditor, NS_QSVC, "tcReasonCode", req.getReasonCode());
        appendElementNS(creditor, NS_QSVC, "tlBusinessRelationIsInterco", "false");
        appendElementNS(creditor, NS_QSVC, "tcBusinessRelationCode", businessRelationCode);
        appendElementNS(creditor, NS_QSVC, "tcCurrencyCode", req.getCurrencyCode());
        appendElementNS(creditor, NS_QSVC, "tcNormalPaymentConditionType", req.getPaymentConditionType());
        appendElementNS(creditor, NS_QSVC, "tcPurchaseGLProfileCode", req.getPurchaseGLProfileCode());
        appendElementNS(creditor, NS_QSVC, "tcBusinessRelationName1", req.getBusinessRelationName1());
        appendElementNS(creditor, NS_QSVC, "tcPurchaseTypeCode", req.getPurchaseTypeCode());
        appendElementNS(creditor, NS_QSVC, "CreditorIsSplitAccount", "false");

        return XmlDocumentHelper.serializeElementToString(root);
    }
}