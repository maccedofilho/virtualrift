variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "Primary GCP region."
  type        = string
}

variable "name" {
  description = "Base name for the VPC resources."
  type        = string
}

variable "subnet_cidr" {
  description = "Primary CIDR for the GKE subnetwork."
  type        = string
}

variable "pod_secondary_range_name" {
  description = "Secondary range name for GKE pods."
  type        = string
}

variable "pod_secondary_cidr" {
  description = "Secondary CIDR for GKE pods."
  type        = string
}

variable "service_secondary_range_name" {
  description = "Secondary range name for GKE services."
  type        = string
}

variable "service_secondary_cidr" {
  description = "Secondary CIDR for GKE services."
  type        = string
}

variable "private_service_range_prefix_length" {
  description = "Prefix length reserved for Private Service Access."
  type        = number
  default     = 16
}

variable "labels" {
  description = "Labels applied to supported resources."
  type        = map(string)
  default     = {}
}
