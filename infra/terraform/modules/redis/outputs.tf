output "name" {
  description = "Redis instance name."
  value       = google_redis_instance.this.name
}

output "host" {
  description = "Redis host."
  value       = google_redis_instance.this.host
}

output "port" {
  description = "Redis port."
  value       = google_redis_instance.this.port
}
