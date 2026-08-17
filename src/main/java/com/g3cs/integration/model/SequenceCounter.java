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
@Document(collection = "sequence_counter")
public class SequenceCounter {
    @Id
    private String id;
    @Field("category")
    private String category;
    @Field("parent_category")
    private String parentCategory;
    @Field("last_number")
    private Long lastNumber;
    @Field("active")
    private Boolean active;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
    @Field("version")
    private Integer version;
}
