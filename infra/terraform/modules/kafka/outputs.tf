output "bootstrap_servers" {
  description = "Kafka bootstrap servers."
  value       = var.bootstrap_servers
}

output "tls_enabled" {
  description = "Whether TLS is required for Kafka connectivity."
  value       = var.tls_enabled
}

output "security_protocol" {
  description = "Kafka client security protocol."
  value       = var.security_protocol
}

output "auth_secret_name" {
  description = "Optional Kubernetes Secret containing Kafka credentials."
  value       = var.auth_secret_name
}

output "sasl_mechanism" {
  description = "Kafka SASL mechanism."
  value       = var.sasl_mechanism
}
