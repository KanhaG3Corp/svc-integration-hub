package com.g3cs.integration.common.message;

public enum MessageCode {
    SUCCESS("Request completed successfully."),
    VALIDATION_FAILED("Some of the information provided is invalid. Please review the highlighted fields."),
    RESOURCE_NOT_FOUND("The requested {resource} could not be found."),
    UNAUTHORIZED("Your session could not be verified. Please sign in again."),
    ACCESS_DENIED("You do not have permission to perform this action."),
    INTERNAL_ERROR("Something went wrong on our end. Please try again."),
    CONNECTION_CREATED("Connection \"{name}\" was created successfully."),
    CONNECTION_UPDATED("Connection \"{name}\" was updated successfully."),
    CONNECTION_DISABLED("Connection \"{name}\" has been disabled."),
    CONNECTION_NOT_FOUND("We couldn't find that connection. It may have been removed."),
    CONNECTION_DUPLICATE_NAME("A connection named \"{name}\" already exists. Please choose a different name."),
    CONNECTION_TEST_SUCCESS("Connection successful. \"{name}\" is ready to use."),
    CONNECTION_TEST_FAILED_AUTH("We couldn't authenticate with \"{name}\". Please check the credentials and try again."),
    CONNECTION_TEST_FAILED_UNREACHABLE("We couldn't reach \"{name}\" at the provided address. Please verify the URL and network access."),
    CONNECTION_TEST_FAILED_UNKNOWN("The connection test did not succeed. Please review the configuration and try again."),
    CONNECTION_AUTH_EXPIRED("The credentials for \"{name}\" have expired. Please reconnect to continue syncing."),
    CONNECTION_NOT_ACTIVE("This connection must be active before it can be used."),
    AUTH_TYPE_UNSUPPORTED("The selected authentication type is not supported for this connector."),
    DISCOVERY_UNAVAILABLE("Automatic discovery isn't available for this connection. You can add the resource manually."),
    INTEGRATION_CREATED("Integration \"{name}\" was created successfully."),
    INTEGRATION_UPDATED("Integration \"{name}\" was updated successfully."),
    INTEGRATION_NOT_FOUND("We couldn't find that integration."),
    INTEGRATION_ACTIVATE_FAILED("This integration cannot be activated until required mapping fields are complete."),
    INTEGRATION_CANCELLED("Integration \"{name}\" has been cancelled and can no longer be edited."),
    INTEGRATION_DISABLED("This integration has been cancelled and can no longer be modified."),
    INTEGRATION_CANCEL_FAILED("Only draft integrations can be cancelled."),
    INTEGRATION_SYNC_STARTED("Sync started for \"{name}\"."),
    INTEGRATION_ALREADY_RUNNING("A sync is already running for this integration. Please wait for it to finish."),
    PAGINATION_REQUIRED("Too many records were returned. Enable pagination and try again."),
    VENDOR_NAME_EXISTS("A vendor with this name already exists."),
    VENDOR_WEBSITE_EXISTS("A vendor with this website already exists."),
    MASTER_RESOLVE_FAILED("We couldn't match \"{value}\" to a master record."),
    ENTITY_RESOLVE_FAILED("We couldn't match entity \"{value}\". Please verify the name in Entity master."),
    ODOO_UPSTREAM_ERROR("The Odoo system returned an error while processing this request."),
    ODOO_MODEL_NOT_FOUND("The Odoo model \"{model}\" was not found. It may not be installed on this instance."),
    FAILED_RECORD_NOT_FOUND("The failed record could not be found."),
    RETRY_QUEUED("Retry has been queued for the selected records.");

    private final String template;

    MessageCode(String template) {
        this.template = template;
    }

    public String template() {
        return template;
    }

    public String resolve(java.util.Map<String, Object> args) {
        String result = template;
        if (args != null) {
            for (java.util.Map.Entry<String, Object> entry : args.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}",
                        entry.getValue() == null ? "" : String.valueOf(entry.getValue()));
            }
        }
        return result;
    }
}
