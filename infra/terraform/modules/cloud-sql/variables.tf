variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "Cloud SQL region."
  type        = string
}

variable "name" {
  description = "Cloud SQL instance name."
  type        = string
}

variable "private_network_self_link" {
  description = "VPC self link for private IP connectivity."
  type        = string
}

variable "database_version" {
  description = "Cloud SQL database engine version."
  type        = string
  default     = "POSTGRES_16"
}

variable "tier" {
  description = "Cloud SQL machine tier."
  type        = string
}

variable "disk_size_gb" {
  description = "Disk size in GB."
  type        = number
}

variable "availability_type" {
  description = "Cloud SQL availability type."
  type        = string
  default     = "REGIONAL"
}

variable "database_names" {
  description = "Logical databases to create in the instance."
  type        = list(string)
}

variable "deletion_protection" {
  description = "Whether deletion protection should be enabled."
  type        = bool
  default     = true
}

variable "labels" {
  description = "Labels applied to supported resources."
  type        = map(string)
  default     = {}
}
