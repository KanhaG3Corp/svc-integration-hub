package com.g3cs.integration.spi;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReadPage {
    @Builder.Default
    private List<Map<String, Object>> records = new ArrayList<>();
    private boolean hasMore;
    private Integer nextOffset;
}
