package com.g3cs.integration.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageQueryRequestDto {
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;
    private String sortBy;
    @Builder.Default
    private String sortDir = "DESC";
    private String search;
}
