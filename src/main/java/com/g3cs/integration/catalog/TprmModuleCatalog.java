package com.g3cs.integration.catalog;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TprmModuleCatalog {

    public static final String VENDOR_MASTER = "vendor_master";

    public List<Map<String, String>> modules() {
        return List.of(Map.of(
                "key", VENDOR_MASTER,
                "label", "Vendor Master",
                "collection", "vendor_master"
        ));
    }

    public List<TprmFieldDefinition> fields(String moduleKey) {
        if (!VENDOR_MASTER.equals(moduleKey)) {
            return List.of();
        }
        return List.of(
                field("vendorName", "Vendor name", "string", true, null),
                field("entityName", "Entity", "string", true, "RESOLVE_ENTITY"),
                field("companyWebsite", "Company website", "url", false, null),
                field("email", "Email", "email", false, null),
                field("phoneNo", "Phone", "number", false, null),
                field("address", "Address", "string", false, null),
                field("location", "Location", "string", false, null),
                field("vendorCategory", "Vendor category", "string", false, "RESOLVE_PARAM"),
                field("businessUnit", "Business unit", "string", false, "RESOLVE_PARAM"),
                field("natureOfEngagement", "Nature of engagement", "list", false, "RESOLVE_PARAM"),
                field("businessFunctions", "Business functions", "list", false, "RESOLVE_PARAM"),
                field("status", "Status", "string", false, "RESOLVE_MASTER"),
                field("remark", "Remark", "string", false, null)
        );
    }

    public List<String> requiredFieldKeys(String moduleKey) {
        return fields(moduleKey).stream()
                .filter(TprmFieldDefinition::isRequired)
                .map(TprmFieldDefinition::getKey)
                .toList();
    }

    private TprmFieldDefinition field(String key, String label, String type, boolean required, String resolveKind) {
        return TprmFieldDefinition.builder()
                .key(key)
                .label(label)
                .type(type)
                .required(required)
                .resolveKind(resolveKind)
                .build();
    }
}
