variable "bootstrap_servers" {
  description = "Kafka bootstrap servers."
  type        = string
}

variable "tls_enabled" {
  description = "Whether TLS is required for Kafka connectivity."
  type        = bool
  default     = true
}

variable "security_protocol" {
  description = "Kafka client security protocol. Managed environments should use SASL_SSL."
  type        = string
  default     = "SASL_SSL"

  validation {
    condition     = contains(["PLAINTEXT", "SSL", "SASL_PLAINTEXT", "SASL_SSL"], var.security_protocol)
    error_message = "security_protocol must be a Kafka-supported protocol."
  }
}

variable "auth_secret_name" {
  description = "Optional Kubernetes Secret that stores Kafka client credentials."
  type        = string
  default     = "virtualrift-kafka-client"
}

variable "sasl_mechanism" {
  description = "Kafka SASL mechanism, when authentication is enabled."
  type        = string
  default     = "SCRAM-SHA-512"

  validation {
    condition     = length(trimspace(var.sasl_mechanism)) > 0
    error_message = "sasl_mechanism must not be empty."
  }
}
