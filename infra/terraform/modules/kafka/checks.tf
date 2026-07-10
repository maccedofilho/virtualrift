check "tls_protocol" {
  assert {
    condition     = var.tls_enabled && var.security_protocol == "SASL_SSL"
    error_message = "Managed VirtualRift Kafka requires tls_enabled=true and security_protocol=SASL_SSL."
  }
}

check "sasl_credentials" {
  assert {
    condition     = var.security_protocol != "SASL_SSL" || length(trimspace(var.auth_secret_name)) > 0
    error_message = "SASL_SSL requires auth_secret_name so credentials can be injected without storing them in Terraform."
  }
}
