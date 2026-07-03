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
