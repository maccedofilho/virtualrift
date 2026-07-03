output "instance_name" {
  description = "Cloud SQL instance name."
  value       = google_sql_database_instance.this.name
}

output "connection_name" {
  description = "Cloud SQL connection name."
  value       = google_sql_database_instance.this.connection_name
}

output "private_ip_address" {
  description = "Cloud SQL private IP address."
  value       = google_sql_database_instance.this.private_ip_address
}

output "database_names" {
  description = "Created logical databases."
  value       = sort(keys(google_sql_database.databases))
}
