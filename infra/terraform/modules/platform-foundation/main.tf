locals {
  labels = merge(
    {
      app         = var.platform_name
      environment = var.environment
      managed_by  = "terraform"
    },
    var.labels
  )

  base_name                    = "${var.platform_name}-${var.environment}"
  pod_secondary_range_name     = "${var.platform_name}-${var.environment}-pods"
  service_secondary_range_name = "${var.platform_name}-${var.environment}-services"
  database_names = [
    "virtualrift_auth",
    "virtualrift_tenant",
    "virtualrift_orchestrator",
    "virtualrift_reports"
  ]
}

resource "google_project_service" "required" {
  for_each           = toset(var.enabled_google_services)
  project            = var.project_id
  service            = each.value
  disable_on_destroy = false
}

module "vpc" {
  source = "../vpc"

  project_id                   = var.project_id
  region                       = var.region
  name                         = local.base_name
  subnet_cidr                  = var.vpc_cidr
  pod_secondary_range_name     = local.pod_secondary_range_name
  pod_secondary_cidr           = var.gke_pods_cidr
  service_secondary_range_name = local.service_secondary_range_name
  service_secondary_cidr       = var.gke_services_cidr
  labels                       = local.labels

  depends_on = [google_project_service.required]
}

module "gke" {
  source = "../gke"

  project_id                   = var.project_id
  location                     = var.region
  name                         = local.base_name
  network_self_link            = module.vpc.network_self_link
  subnetwork_self_link         = module.vpc.subnetwork_self_link
  pod_secondary_range_name     = module.vpc.pod_secondary_range_name
  service_secondary_range_name = module.vpc.service_secondary_range_name
  master_ipv4_cidr_block       = var.master_ipv4_cidr_block
  machine_type                 = var.node_machine_type
  disk_size_gb                 = var.node_disk_size_gb
  min_node_count               = var.node_min_count
  max_node_count               = var.node_max_count
  deletion_protection          = var.deletion_protection
  labels                       = local.labels
  network_tags                 = [local.base_name]

  depends_on = [module.vpc]
}

module "cloud_sql" {
  source = "../cloud-sql"

  project_id                = var.project_id
  region                    = var.region
  name                      = "${local.base_name}-postgres"
  private_network_self_link = module.vpc.network_self_link
  tier                      = var.sql_tier
  disk_size_gb              = var.sql_disk_size_gb
  availability_type         = var.sql_availability_type
  database_names            = local.database_names
  deletion_protection       = var.deletion_protection
  labels                    = local.labels

  depends_on = [module.vpc]
}

module "redis" {
  source = "../redis"

  project_id         = var.project_id
  region             = var.region
  name               = "${local.base_name}-redis"
  authorized_network = module.vpc.network_self_link
  memory_size_gb     = var.redis_memory_size_gb
  tier               = var.redis_tier
  labels             = local.labels

  depends_on = [module.vpc]
}

module "gcs" {
  source = "../gcs"

  project_id    = var.project_id
  name          = "${local.base_name}-reports"
  location      = var.reports_bucket_location
  force_destroy = var.reports_bucket_force_destroy
  labels        = local.labels

  depends_on = [google_project_service.required]
}

module "kafka" {
  source = "../kafka"

  bootstrap_servers = var.kafka_bootstrap_servers
  tls_enabled       = var.kafka_tls_enabled
  auth_secret_name  = var.kafka_auth_secret_name
}

module "vault" {
  source = "../vault"

  address         = var.vault_address
  namespace       = var.vault_namespace
  auth_path       = var.vault_auth_path
  kubernetes_role = var.vault_kubernetes_role
}
