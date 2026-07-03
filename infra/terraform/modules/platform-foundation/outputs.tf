output "network" {
  description = "Networking outputs."
  value = {
    name                    = module.vpc.network_name
    self_link               = module.vpc.network_self_link
    subnetwork_name         = module.vpc.subnetwork_name
    subnetwork_self_link    = module.vpc.subnetwork_self_link
    pod_secondary_range     = module.vpc.pod_secondary_range_name
    service_secondary_range = module.vpc.service_secondary_range_name
  }
}

output "gke" {
  description = "GKE outputs."
  value = {
    name                       = module.gke.name
    location                   = module.gke.location
    endpoint                   = module.gke.endpoint
    node_service_account_email = module.gke.node_service_account_email
  }
}

output "cloud_sql" {
  description = "Cloud SQL outputs."
  value = {
    instance_name      = module.cloud_sql.instance_name
    connection_name    = module.cloud_sql.connection_name
    private_ip_address = module.cloud_sql.private_ip_address
    database_names     = module.cloud_sql.database_names
  }
}

output "redis" {
  description = "Redis outputs."
  value = {
    name = module.redis.name
    host = module.redis.host
    port = module.redis.port
  }
}

output "gcs" {
  description = "GCS outputs."
  value = {
    bucket_name = module.gcs.bucket_name
    bucket_url  = module.gcs.bucket_url
  }
}

output "kafka" {
  description = "Kafka integration outputs."
  value = {
    bootstrap_servers = module.kafka.bootstrap_servers
    tls_enabled       = module.kafka.tls_enabled
    auth_secret_name  = module.kafka.auth_secret_name
    sasl_mechanism    = module.kafka.sasl_mechanism
  }
}

output "vault" {
  description = "Vault integration outputs."
  value = {
    address         = module.vault.address
    namespace       = module.vault.namespace
    auth_path       = module.vault.auth_path
    kubernetes_role = module.vault.kubernetes_role
  }
}

output "helm_values" {
  description = "Suggested values payload to bridge Terraform outputs into the Helm chart."
  value = {
    platform = {
      publicApiUrl   = var.public_api_url
      frontendOrigin = var.frontend_origin
    }
    dependencies = {
      postgres = {
        host = module.cloud_sql.private_ip_address
        port = 5432
      }
      redis = {
        host = module.redis.host
        port = module.redis.port
      }
      kafka = {
        bootstrapServers = module.kafka.bootstrap_servers
      }
    }
    reportsBucket = module.gcs.bucket_name
  }
  sensitive = true
}
