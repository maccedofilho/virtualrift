output "name" {
  description = "GKE cluster name."
  value       = google_container_cluster.this.name
}

output "location" {
  description = "GKE cluster location."
  value       = google_container_cluster.this.location
}

output "endpoint" {
  description = "GKE cluster endpoint."
  value       = google_container_cluster.this.endpoint
}

output "fleet_membership_name" {
  description = "Fleet membership used by the Connect Gateway."
  value       = try(google_gke_hub_membership.this[0].membership_id, null)
}

output "ca_certificate" {
  description = "GKE cluster CA certificate."
  value       = google_container_cluster.this.master_auth[0].cluster_ca_certificate
  sensitive   = true
}

output "node_service_account_email" {
  description = "Service account used by GKE nodes."
  value       = local.node_service_account_email
}
