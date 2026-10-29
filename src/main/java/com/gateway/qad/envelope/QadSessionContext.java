package com.gateway.qad.envelope;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QadSessionContext {
    private String domain;
    private String version;
    private String entity;
    private String action;
    private String scopeTransaction;
    private String mnemonicsRaw;
    private String username;
    private String password;
    private Map<String, String> additionalProperties;
}
