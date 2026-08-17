package com.g3cs.integration.catalog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TprmFieldDefinition {
    private String key;
    private String label;
    private String type;
    private boolean required;
    private String resolveKind;
}
