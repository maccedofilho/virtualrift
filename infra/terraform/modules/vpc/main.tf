resource "google_compute_network" "this" {
  project                 = var.project_id
  name                    = var.name
  auto_create_subnetworks = false
  routing_mode            = "REGIONAL"
}

resource "google_compute_subnetwork" "gke" {
  project                  = var.project_id
  region                   = var.region
  name                     = "${var.name}-gke"
  network                  = google_compute_network.this.id
  ip_cidr_range            = var.subnet_cidr
  private_ip_google_access = true

  secondary_ip_range {
    range_name    = var.pod_secondary_range_name
    ip_cidr_range = var.pod_secondary_cidr
  }

  secondary_ip_range {
    range_name    = var.service_secondary_range_name
    ip_cidr_range = var.service_secondary_cidr
  }
}

resource "google_compute_global_address" "private_service_access" {
  project       = var.project_id
  name          = "${var.name}-private-service-access"
  purpose       = "VPC_PEERING"
  address_type  = "INTERNAL"
  prefix_length = var.private_service_range_prefix_length
  network       = google_compute_network.this.id
}

resource "google_service_networking_connection" "private_vpc_connection" {
  network                 = google_compute_network.this.id
  service                 = "servicenetworking.googleapis.com"
  reserved_peering_ranges = [google_compute_global_address.private_service_access.name]
}
