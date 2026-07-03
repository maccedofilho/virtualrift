output "bucket_name" {
  description = "Reports bucket name."
  value       = google_storage_bucket.this.name
}

output "bucket_url" {
  description = "GCS URL for the reports bucket."
  value       = google_storage_bucket.this.url
}
