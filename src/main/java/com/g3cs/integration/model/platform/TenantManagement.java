package com.g3cs.integration.model.platform;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = TenantManagement.COLLECTION)
public class TenantManagement {
    public static final String COLLECTION = "tenant_management";

    @Id
    private String id;
    @Field("tenant_id")
    private String tenantId;
    @Field("tenant_db")
    private String tenantDb;
    @Field("is_enabled")
    private Boolean isEnabled;
    @Field("is_deleted")
    private Boolean isDeleted;
    @Field("is_drafted")
    private Boolean isDrafted;
    @Field("is_onboarded")
    private Boolean isOnboarded;
}
