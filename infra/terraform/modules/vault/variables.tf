variable "address" {
  description = "Vault base URL."
  type        = string
}

variable "namespace" {
  description = "Optional Vault namespace."
  type        = string
  default     = ""
}

variable "auth_path" {
  description = "Vault auth mount used by Kubernetes workloads."
  type        = string
  default     = "auth/kubernetes"
}

variable "kubernetes_role" {
  description = "Vault role used by application workloads."
  type        = string
  default     = "virtualrift"
}

variable "secret_store_name" {
  description = "Namespaced External Secrets SecretStore name."
  type        = string
  default     = "virtualrift-vault"
}

variable "kv_path" {
  description = "Vault KV secrets engine mount path."
  type        = string
  default     = "virtualrift"
}

variable "secret_path_prefix" {
  description = "Environment-specific path below the Vault KV mount."
  type        = string
}
