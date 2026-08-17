package com.g3cs.integration.model;

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
@Document(collection = "entity_details")
public class EntityDetails {
    @Id
    private String id;
    @Field("region_id")
    private String regionId;
    @Field("entity_code")
    private String entityCode;
    @Field("entity_name")
    private String entityName;
    @Field("is_enable")
    private Boolean isEnable;
    @Field("is_deleted")
    private Boolean isDeleted;
}
