package com.gateway.qad.registry;

import com.gateway.catalog.GatewayCatalogRepository;
import com.gateway.catalog.GatewayServiceEntity;
import com.gateway.qad.mapper.QadBusinessMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro centralizado de servicios QAD.
 * Permite resolver mappers en caliente tanto por inyección de beans como por catálogo en base de datos.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QadServiceRegistry {

    private final Optional<GatewayCatalogRepository> catalogRepository;
    private final List<QadBusinessMapper<?>> mappers;

    private final Map<String, QadBusinessMapper<?>> mapperMap = new ConcurrentHashMap<>();

    @jakarta.annotation.PostConstruct
    public void init() {
        for (QadBusinessMapper<?> mapper : mappers) {
            registerMapper(mapper);
        }
        log.info("QadServiceRegistry inicializado con {} mappers: {}", mapperMap.size(), mapperMap.keySet());
    }

    public void registerMapper(QadBusinessMapper<?> mapper) {
        String key = mapper.getServiceCode().toUpperCase();
        mapperMap.put(key, mapper);
        log.info("Mapper registrado: serviceCode={}, operationName={}, class={}",
                key, mapper.getOperationName(), mapper.getClass().getSimpleName());
    }

    @SuppressWarnings("unchecked")
    public <T> QadBusinessMapper<T> getMapper(String serviceCode) {
        if (serviceCode == null) return null;
        return (QadBusinessMapper<T>) mapperMap.get(serviceCode.toUpperCase());
    }


    public boolean hasService(String serviceCode) {
        return serviceCode != null && mapperMap.containsKey(serviceCode.toUpperCase());
    }

    public Set<String> getRegisteredServiceCodes() {
        return Collections.unmodifiableSet(mapperMap.keySet());
    }

    public Optional<GatewayServiceEntity> getServiceEntity(String serviceCode) {
        if (catalogRepository.isPresent()) {
            return catalogRepository.get().findServiceByCode(serviceCode);
        }
        return Optional.empty();
    }
}
