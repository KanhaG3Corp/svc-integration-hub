package com.g3cs.integration.catalog;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class TprmModuleCatalog {

    /** Temporary Hub target — production vendor_master is not used for now. */
    public static final String VENDOR_MASTER_TEST = "vendor_master_test";

    public static final String COLLECTION_VENDOR_MASTER_TEST = "vendor_master_test";

    public List<Map<String, String>> modules() {
        return List.of(Map.of(
                "key", VENDOR_MASTER_TEST,
                "label", "Vendor Master Test",
                "collection", COLLECTION_VENDOR_MASTER_TEST
        ));
    }

    public List<TprmFieldDefinition> fields(String moduleKey) {
        if (!VENDOR_MASTER_TEST.equals(moduleKey)) {
            return List.of();
        }
        return vendorFields();
    }

    public List<String> requiredFieldKeys(String moduleKey) {
        return fields(moduleKey).stream()
                .filter(TprmFieldDefinition::isRequired)
                .map(TprmFieldDefinition::getKey)
                .toList();
    }

    /** Plain string test fields — no required flags, no resolve kinds. */
    private List<TprmFieldDefinition> vendorFields() {
        return List.of(
                field("vendorName", "Vendor name"),
                field("email", "Email"),
                field("phoneNo", "Phone"),
                field("address", "Address"),
                field("location", "Location"),
                field("companyWebsite", "Company website"),
                field("status", "Status"),
                field("remark", "Remark")
        );
    }

    private TprmFieldDefinition field(String key, String label) {
        return TprmFieldDefinition.builder()
                .key(key)
                .label(label)
                .type("string")
                .required(false)
                .resolveKind(null)
                .build();
    }
}
