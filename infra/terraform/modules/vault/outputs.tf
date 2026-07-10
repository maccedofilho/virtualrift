output "address" {
  description = "Vault base URL."
  value       = var.address
}

output "namespace" {
  description = "Vault namespace."
  value       = var.namespace
}

output "auth_path" {
  description = "Vault auth mount."
  value       = var.auth_path
}

output "kubernetes_role" {
  description = "Vault Kubernetes role."
  value       = var.kubernetes_role
}

output "secret_store_name" {
  description = "External Secrets SecretStore name."
  value       = var.secret_store_name
}

output "kv_path" {
  description = "Vault KV secrets engine mount path."
  value       = var.kv_path
}

output "secret_path_prefix" {
  description = "Environment-specific Vault secret path prefix."
  value       = var.secret_path_prefix
}
