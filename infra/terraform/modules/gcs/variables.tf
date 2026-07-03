variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "name" {
  description = "Bucket name."
  type        = string
}

variable "location" {
  description = "Bucket location."
  type        = string
}

variable "force_destroy" {
  description = "Whether Terraform can destroy the bucket with objects inside."
  type        = bool
  default     = false
}

variable "labels" {
  description = "Labels applied to the bucket."
  type        = map(string)
  default     = {}
}

variable "lifecycle_age_days" {
  description = "Age in days before artifacts become eligible for deletion."
  type        = number
  default     = 90
}
