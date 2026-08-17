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
@Document(collection = "master_data")
public class MasterData {
    @Id
    private String id;
    @Field("master_group_id")
    private String masterGroupId;
    @Field("master_group_code")
    private String masterGroupCode;
    @Field("master_code")
    private String masterCode;
    @Field("master_name")
    private String masterName;
    @Field("is_enable")
    private Boolean isEnable;
    @Field("is_deleted")
    private Boolean isDeleted;
}
