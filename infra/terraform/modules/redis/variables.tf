variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "region" {
  description = "Redis region."
  type        = string
}

variable "name" {
  description = "Redis instance name."
  type        = string
}

variable "authorized_network" {
  description = "VPC self link used by the Redis instance."
  type        = string
}

variable "memory_size_gb" {
  description = "Redis memory size in GB."
  type        = number
}

variable "tier" {
  description = "Redis tier."
  type        = string
}

variable "labels" {
  description = "Labels applied to supported resources."
  type        = map(string)
  default     = {}
}
