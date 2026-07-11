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

variable "backup_start_time" {
  description = "UTC start time for the daily automated backup window."
  type        = string
  default     = "01:00"

  validation {
    condition     = can(regex("^(?:[01][0-9]|2[0-3]):[0-5][0-9]$", var.backup_start_time))
    error_message = "backup_start_time must use 24-hour HH:MM format."
  }
}

variable "retained_backups" {
  description = "Number of successful automated backups retained by Cloud SQL."
  type        = number
  default     = 30

  validation {
    condition     = var.retained_backups >= 7 && var.retained_backups <= 365
    error_message = "retained_backups must be between 7 and 365."
  }
}

variable "transaction_log_retention_days" {
  description = "Number of days of PostgreSQL transaction logs retained for PITR."
  type        = number
  default     = 7

  validation {
    condition     = var.transaction_log_retention_days >= 1 && var.transaction_log_retention_days <= 7
    error_message = "transaction_log_retention_days must be between 1 and 7 for the configured Cloud SQL edition."
  }
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
