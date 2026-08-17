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
@Document(collection = "region_details")
public class RegionDetails {
    @Id
    private String id;
    @Field("region_code")
    private String regionCode;
    @Field("region_name")
    private String regionName;
    @Field("is_enable")
    private Boolean isEnable;
    @Field("is_deleted")
    private Boolean isDeleted;
}
