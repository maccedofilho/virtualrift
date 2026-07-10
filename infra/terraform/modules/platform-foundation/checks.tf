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
