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
@Document(collection = "business_param_dtl")
public class BusinessParamDtl {
    @Id
    private String id;
    @Field("param_group_id")
    private String paramGroupId;
    @Field("param_group_name")
    private String paramGroupName;
    @Field("param_code")
    private String paramCode;
    @Field("param_desc")
    private String paramDesc;
    @Field("is_enable")
    private Boolean isEnable;
    @Field("is_deleted")
    private Boolean isDeleted;
}
