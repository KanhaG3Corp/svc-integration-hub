package com.g3cs.integration.common.mongo;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
public abstract class AuditableDocument {

    @Field("created_by")
    private String createdBy;

    @Field("created_dt")
    private Instant createdDt;

    @Field("updated_by")
    private String updatedBy;

    @Field("updated_dt")
    private Instant updatedDt;

    @Field("is_deleted")
    private Boolean isDeleted;
}
