output "network_name" {
  description = "VPC network name."
  value       = google_compute_network.this.name
}

output "network_self_link" {
  description = "VPC network self link."
  value       = google_compute_network.this.self_link
}

output "subnetwork_name" {
  description = "Primary GKE subnetwork name."
  value       = google_compute_subnetwork.gke.name
}

output "subnetwork_self_link" {
  description = "Primary GKE subnetwork self link."
  value       = google_compute_subnetwork.gke.self_link
}

output "pod_secondary_range_name" {
  description = "GKE pods secondary range name."
  value       = var.pod_secondary_range_name
}

output "service_secondary_range_name" {
  description = "GKE services secondary range name."
  value       = var.service_secondary_range_name
}

output "private_service_connection" {
  description = "Private Service Access connection name."
  value       = google_service_networking_connection.private_vpc_connection.peering
}
