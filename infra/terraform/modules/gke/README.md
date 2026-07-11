# GKE Module

This module groups the infrastructure concerns for the GKE cluster used by VirtualRift. It covers node pools, autoscaling behavior, private control-plane networking, and Fleet registration for Connect Gateway access.

For an existing cluster, first apply with `enable_private_endpoint=false`, configure Gateway IAM and RBAC for the deployment identity, verify access through the Fleet membership, and only then apply with the default `true` value.
