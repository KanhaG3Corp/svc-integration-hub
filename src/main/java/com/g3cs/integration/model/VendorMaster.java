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
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "vendor_master")
public class VendorMaster {
    @Id
    private String id;
    @Field("vendor_id")
    private String vendorId;
    @Field("vendor_name")
    private String vendorName;
    @Field("tenant_id")
    private String tenantId;
    @Field("region_ids")
    private List<String> regionIds;
    @Field("entity_ids")
    private List<String> entityIds;
    @Field("email")
    private String email;
    @Field("phone_no")
    private Number phoneNo;
    @Field("address")
    private String address;
    @Field("onboarded_status")
    private String onboardedStatus;
    @Field("remark")
    private String remark;
    @Field("status")
    private String status;
    @Field("is_draft")
    private Boolean isDraft;
    @Field("company_website")
    private String companyWebsite;
    @Field("business_unit")
    private String businessUnit;
    @Field("nature_of_engagement")
    private List<String> natureOfEngagement;
    @Field("business_functions")
    private List<String> businessFunctions;
    @Field("vendor_category")
    private String vendorCategory;
    @Field("location")
    private String location;
    @Field("is_enable")
    private Boolean isEnable;
    @Field("is_deleted")
    private Boolean isDeleted;
    @Field("created_by")
    private String createdBy;
    @Field("created_dt")
    private Instant createdDt;
    @Field("updated_by")
    private String updatedBy;
    @Field("updated_dt")
    private Instant updatedDt;
    @Field("is_vendor_onbd")
    private Boolean isVendorOnbd;
}
