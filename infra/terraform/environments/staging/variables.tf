variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "Primary region."
  type        = string
  default     = "us-central1"
}

variable "platform_name" {
  description = "Base platform name."
  type        = string
  default     = "virtualrift"
}

variable "public_api_url" {
  description = "Public API URL."
  type        = string
}

variable "frontend_origin" {
  description = "Frontend origin."
  type        = string
}

variable "labels" {
  description = "Additional labels."
  type        = map(string)
  default     = {}
}

variable "vpc_cidr" {
  description = "Primary subnet CIDR."
  type        = string
  default     = "10.60.0.0/20"
}

variable "gke_pods_cidr" {
  description = "Pods secondary CIDR."
  type        = string
  default     = "10.64.0.0/16"
}

variable "gke_services_cidr" {
  description = "Services secondary CIDR."
  type        = string
  default     = "10.68.0.0/20"
}

variable "master_ipv4_cidr_block" {
  description = "Private control plane CIDR."
  type        = string
  default     = "172.17.0.0/28"
}

variable "node_machine_type" {
  description = "GKE node machine type."
  type        = string
  default     = "e2-standard-4"
}

variable "node_min_count" {
  description = "Minimum node count."
  type        = number
  default     = 2
}

variable "node_max_count" {
  description = "Maximum node count."
  type        = number
  default     = 5
}

variable "node_disk_size_gb" {
  description = "GKE node disk size."
  type        = number
  default     = 120
}

variable "sql_tier" {
  description = "Cloud SQL tier."
  type        = string
  default     = "db-custom-4-15360"
}

variable "sql_disk_size_gb" {
  description = "Cloud SQL disk size."
  type        = number
  default     = 200
}

variable "sql_availability_type" {
  description = "Cloud SQL availability mode."
  type        = string
  default     = "REGIONAL"
}

variable "redis_memory_size_gb" {
  description = "Redis memory size."
  type        = number
  default     = 4
}

variable "redis_tier" {
  description = "Redis tier."
  type        = string
  default     = "STANDARD_HA"
}

variable "reports_bucket_location" {
  description = "Reports bucket location."
  type        = string
  default     = "US"
}

variable "reports_bucket_force_destroy" {
  description = "Whether Terraform may destroy the bucket with objects."
  type        = bool
  default     = false
}

variable "kafka_bootstrap_servers" {
  description = "External Kafka bootstrap servers."
  type        = string
}

variable "kafka_tls_enabled" {
  description = "Whether Kafka uses TLS."
  type        = bool
  default     = true
}

variable "kafka_auth_secret_name" {
  description = "Optional Kubernetes Secret name with Kafka credentials."
  type        = string
  default     = ""
}

variable "vault_address" {
  description = "External Vault URL."
  type        = string
  default     = ""
}

variable "vault_namespace" {
  description = "Vault namespace."
  type        = string
  default     = ""
}

variable "vault_auth_path" {
  description = "Vault auth path."
  type        = string
  default     = "auth/kubernetes"
}

variable "vault_kubernetes_role" {
  description = "Vault Kubernetes role."
  type        = string
  default     = "virtualrift-staging"
}

variable "deletion_protection" {
  description = "Whether stateful resources are protected from deletion."
  type        = bool
  default     = true
}
