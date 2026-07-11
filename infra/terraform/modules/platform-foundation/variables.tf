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

variable "gke_enable_private_endpoint" {
  description = "Whether the GKE control plane is reachable only through its private endpoint."
  type        = bool
  default     = true
}

variable "gke_master_authorized_networks" {
  description = "Temporary CIDRs allowed to reach a public GKE endpoint during migration."
  type = list(object({
    cidr_block   = string
    display_name = string
  }))
  default = []
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

variable "sql_backup_start_time" {
  description = "UTC start time for the daily Cloud SQL backup window."
  type        = string
  default     = "01:00"
}

variable "sql_retained_backups" {
  description = "Number of successful automated Cloud SQL backups to retain."
  type        = number
  default     = 30
}

variable "sql_transaction_log_retention_days" {
  description = "Number of days of Cloud SQL transaction logs retained for PITR."
  type        = number
  default     = 7
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

variable "kafka_security_protocol" {
  description = "Kafka client security protocol."
  type        = string
  default     = "SASL_SSL"
}

variable "kafka_auth_secret_name" {
  description = "Optional Kubernetes Secret that stores Kafka client credentials."
  type        = string
  default     = "virtualrift-kafka-client"
}

variable "kafka_sasl_mechanism" {
  description = "Kafka SASL authentication mechanism."
  type        = string
  default     = "SCRAM-SHA-512"
}

variable "external_secrets_enabled" {
  description = "Whether Helm should reconcile application Secrets from Vault through External Secrets Operator."
  type        = bool
  default     = false
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

variable "vault_secret_store_name" {
  description = "Namespaced External Secrets SecretStore name."
  type        = string
  default     = "virtualrift-vault"
}

variable "vault_kv_path" {
  description = "Vault KV secrets engine mount path."
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
    "connectgateway.googleapis.com",
    "gkeconnect.googleapis.com",
    "gkehub.googleapis.com",
    "cloudresourcemanager.googleapis.com",
    "sqladmin.googleapis.com",
    "redis.googleapis.com",
    "servicenetworking.googleapis.com",
    "storage.googleapis.com",
    "iam.googleapis.com",
    "secretmanager.googleapis.com"
  ]
}
