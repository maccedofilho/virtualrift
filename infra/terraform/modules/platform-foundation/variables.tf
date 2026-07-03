variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "Primary GCP region."
  type        = string
}

variable "environment" {
  description = "Environment identifier."
  type        = string
}

variable "platform_name" {
  description = "Base platform name used in resource naming."
  type        = string
  default     = "virtualrift"
}

variable "public_api_url" {
  description = "Public API URL used by the platform."
  type        = string
}

variable "frontend_origin" {
  description = "Frontend origin used in CORS and OAuth redirects."
  type        = string
}

variable "labels" {
  description = "Common labels."
  type        = map(string)
  default     = {}
}

variable "vpc_cidr" {
  description = "Primary CIDR for the GKE subnetwork."
  type        = string
}

variable "gke_pods_cidr" {
  description = "Secondary CIDR for GKE pods."
  type        = string
}

variable "gke_services_cidr" {
  description = "Secondary CIDR for GKE services."
  type        = string
}

variable "master_ipv4_cidr_block" {
  description = "Private control plane CIDR block."
  type        = string
}

variable "node_machine_type" {
  description = "Default GKE node machine type."
  type        = string
}

variable "node_min_count" {
  description = "Minimum node count."
  type        = number
}

variable "node_max_count" {
  description = "Maximum node count."
  type        = number
}

variable "node_disk_size_gb" {
  description = "Default GKE node disk size in GB."
  type        = number
}

variable "sql_tier" {
  description = "Cloud SQL machine tier."
  type        = string
}

variable "sql_disk_size_gb" {
  description = "Cloud SQL disk size in GB."
  type        = number
}

variable "sql_availability_type" {
  description = "Cloud SQL availability type."
  type        = string
  default     = "REGIONAL"
}

variable "redis_memory_size_gb" {
  description = "Redis memory size in GB."
  type        = number
}

variable "redis_tier" {
  description = "Redis service tier."
  type        = string
}

variable "reports_bucket_location" {
  description = "GCS location used by the reports bucket."
  type        = string
}

variable "reports_bucket_force_destroy" {
  description = "Whether Terraform may destroy the reports bucket with objects inside."
  type        = bool
  default     = false
}

variable "kafka_bootstrap_servers" {
  description = "External Kafka bootstrap servers."
  type        = string
}

variable "kafka_tls_enabled" {
  description = "Whether TLS is required for Kafka connectivity."
  type        = bool
  default     = true
}

variable "kafka_auth_secret_name" {
  description = "Optional Kubernetes Secret that stores Kafka client credentials."
  type        = string
  default     = ""
}

variable "vault_address" {
  description = "External Vault address."
  type        = string
  default     = ""
}

variable "vault_namespace" {
  description = "Optional Vault namespace."
  type        = string
  default     = ""
}

variable "vault_auth_path" {
  description = "Vault Kubernetes auth path."
  type        = string
  default     = "auth/kubernetes"
}

variable "vault_kubernetes_role" {
  description = "Vault Kubernetes role used by workloads."
  type        = string
  default     = "virtualrift"
}

variable "deletion_protection" {
  description = "Whether to protect stateful resources from deletion."
  type        = bool
  default     = true
}

variable "enabled_google_services" {
  description = "Google APIs required by the platform foundation."
  type        = list(string)
  default = [
    "compute.googleapis.com",
    "container.googleapis.com",
    "sqladmin.googleapis.com",
    "redis.googleapis.com",
    "servicenetworking.googleapis.com",
    "storage.googleapis.com",
    "iam.googleapis.com",
    "secretmanager.googleapis.com"
  ]
}
