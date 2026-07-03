variable "bootstrap_servers" {
  description = "Kafka bootstrap servers."
  type        = string
}

variable "tls_enabled" {
  description = "Whether TLS is required for Kafka connectivity."
  type        = bool
  default     = true
}

variable "auth_secret_name" {
  description = "Optional Kubernetes Secret that stores Kafka client credentials."
  type        = string
  default     = ""
}

variable "sasl_mechanism" {
  description = "Kafka SASL mechanism, when authentication is enabled."
  type        = string
  default     = "SCRAM-SHA-512"
}
