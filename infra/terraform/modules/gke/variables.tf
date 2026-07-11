variable "project_id" {
  description = "GCP project id."
  type        = string
}

variable "location" {
  description = "Cluster location."
  type        = string
}

variable "name" {
  description = "Cluster name."
  type        = string
}

variable "network_self_link" {
  description = "VPC self link."
  type        = string
}

variable "subnetwork_self_link" {
  description = "Subnetwork self link."
  type        = string
}

variable "pod_secondary_range_name" {
  description = "Secondary range name used for pods."
  type        = string
}

variable "service_secondary_range_name" {
  description = "Secondary range name used for services."
  type        = string
}

variable "master_ipv4_cidr_block" {
  description = "CIDR block reserved for the private GKE control plane."
  type        = string
}

variable "enable_private_endpoint" {
  description = "Whether the GKE control plane is reachable only through its private endpoint."
  type        = bool
  default     = true
}

variable "master_authorized_networks" {
  description = "Temporary CIDRs allowed to reach a public control-plane endpoint during migration."
  type = list(object({
    cidr_block   = string
    display_name = string
  }))
  default = []
}

variable "register_fleet_membership" {
  description = "Whether to register the cluster in the project fleet for Connect Gateway access."
  type        = bool
  default     = true
}

variable "release_channel" {
  description = "GKE release channel."
  type        = string
  default     = "REGULAR"
}

variable "machine_type" {
  description = "Default node pool machine type."
  type        = string
}

variable "disk_size_gb" {
  description = "Node pool disk size."
  type        = number
}

variable "min_node_count" {
  description = "Minimum node count."
  type        = number
}

variable "max_node_count" {
  description = "Maximum node count."
  type        = number
}

variable "node_service_account_email" {
  description = "Optional existing service account for GKE nodes."
  type        = string
  default     = ""
}

variable "create_node_service_account" {
  description = "Whether the module should create a node service account."
  type        = bool
  default     = true
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

variable "network_tags" {
  description = "Network tags for GKE nodes."
  type        = list(string)
  default     = []
}
