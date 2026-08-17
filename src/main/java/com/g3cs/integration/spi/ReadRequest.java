package com.g3cs.integration.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadRequest {
    private String resourceKey;
    @Builder.Default
    private List<String> fields = new ArrayList<>();
    @Builder.Default
    private List<Object> domain = new ArrayList<>();
    private Integer offset;
    private Integer limit;
    private String order;
}
