resource "google_redis_instance" "this" {
  project                 = var.project_id
  region                  = var.region
  name                    = var.name
  tier                    = var.tier
  memory_size_gb          = var.memory_size_gb
  redis_version           = "REDIS_7_0"
  authorized_network      = var.authorized_network
  connect_mode            = "PRIVATE_SERVICE_ACCESS"
  display_name            = var.name
  labels                  = var.labels
  transit_encryption_mode = "SERVER_AUTHENTICATION"
}
