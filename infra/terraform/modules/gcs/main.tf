resource "google_storage_bucket" "this" {
  project                     = var.project_id
  name                        = var.name
  location                    = var.location
  force_destroy               = var.force_destroy
  storage_class               = "STANDARD"
  labels                      = var.labels
  uniform_bucket_level_access = true

  versioning {
    enabled = true
  }

  lifecycle_rule {
    condition {
      age = var.lifecycle_age_days
    }

    action {
      type = "Delete"
    }
  }
}
