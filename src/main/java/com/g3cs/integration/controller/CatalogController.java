package com.g3cs.integration.controller;

import com.g3cs.integration.catalog.TprmModuleCatalog;
import com.g3cs.integration.common.dto.ApiResponseDto;
import com.g3cs.integration.common.message.MessageCode;
import com.g3cs.integration.model.ApplicationDocument;
import com.g3cs.integration.model.ConnectorDocument;
import com.g3cs.integration.service.CatalogSeedService;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/integration-hub")
public class CatalogController {

    private final MongoTemplate mongoTemplate;
    private final CatalogSeedService catalogSeedService;
    private final TprmModuleCatalog tprmModuleCatalog;

    public CatalogController(MongoTemplate mongoTemplate,
                             CatalogSeedService catalogSeedService,
                             TprmModuleCatalog tprmModuleCatalog) {
        this.mongoTemplate = mongoTemplate;
        this.catalogSeedService = catalogSeedService;
        this.tprmModuleCatalog = tprmModuleCatalog;
    }

    @GetMapping("/applications")
    public ApiResponseDto<List<ApplicationDocument>> applications() {
        catalogSeedService.ensureTenantCatalog();
        List<ApplicationDocument> items = mongoTemplate.find(
                Query.query(Criteria.where("is_deleted").ne(true)), ApplicationDocument.class);
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), items);
    }

    @GetMapping("/connectors")
    public ApiResponseDto<List<ConnectorDocument>> connectors(
            @RequestParam(required = false) String applicationId) {
        catalogSeedService.ensureTenantCatalog();
        Criteria criteria = Criteria.where("is_deleted").ne(true);
        if (applicationId != null && !applicationId.isBlank()) {
            criteria = criteria.and("application_id").is(applicationId);
        }
        return ApiResponseDto.success(MessageCode.SUCCESS.template(),
                mongoTemplate.find(Query.query(criteria), ConnectorDocument.class));
    }

    @GetMapping("/tprm-modules")
    public ApiResponseDto<?> tprmModules() {
        catalogSeedService.ensureTenantCatalog();
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), tprmModuleCatalog.modules());
    }

    @GetMapping("/tprm-modules/{moduleKey}/fields")
    public ApiResponseDto<?> tprmFields(@PathVariable String moduleKey) {
        catalogSeedService.ensureTenantCatalog();
        return ApiResponseDto.success(MessageCode.SUCCESS.template(), tprmModuleCatalog.fields(moduleKey));
    }
}
