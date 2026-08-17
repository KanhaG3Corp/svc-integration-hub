package com.g3cs.integration.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "sequence_master")
public class SequenceMaster {
    @Id
    private String id;
    @Field("category")
    private String category;
    @Field("prefix")
    private String prefix;
    @Field("suffix")
    private String suffix;
    @Field("number_of_digits")
    private Integer numberOfDigits;
    @Field("last_number")
    private Long lastNumber;
    @Field("active")
    private Boolean active;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
    @Field("created_by")
    private String createdBy;
    @Field("updated_by")
    private String updatedBy;
    @Field("version")
    private Integer version;
}
