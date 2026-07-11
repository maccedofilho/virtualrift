check "external_secrets_vault_contract" {
  assert {
    condition = !var.external_secrets_enabled || (
      length(trimspace(var.vault_address)) > 0 &&
      length(trimspace(var.vault_kubernetes_role)) > 0 &&
      length(trimspace(var.vault_secret_store_name)) > 0 &&
      length(trimspace(var.vault_kv_path)) > 0
    )
    error_message = "External Secrets requires a Vault address, Kubernetes role, SecretStore name and KV path."
  }
}

check "production_resilience_contract" {
  assert {
    condition = var.environment != "production" || (
      var.sql_availability_type == "REGIONAL" &&
      var.sql_retained_backups >= 30 &&
      var.sql_transaction_log_retention_days == 7 &&
      var.redis_tier == "STANDARD_HA" &&
      var.deletion_protection
    )
    error_message = "Production requires regional Cloud SQL, 30 backups, 7-day PITR logs, HA Redis and deletion protection."
  }
}

check "staging_resilience_contract" {
  assert {
    condition = var.environment != "staging" || (
      var.sql_availability_type == "REGIONAL" &&
      var.sql_retained_backups >= 14 &&
      var.redis_tier == "STANDARD_HA"
    )
    error_message = "Staging requires regional Cloud SQL, at least 14 backups and HA Redis."
  }
}
