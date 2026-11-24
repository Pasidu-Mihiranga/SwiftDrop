variable "region" {
  description = "AWS region. Both instances stay in one zone of this region."
  type        = string
  default     = "ap-south-1"
}

variable "admin_cidr" {
  description = "Your public IP with /32, for SSH only. Example: 203.0.113.10/32"
  type        = string

  validation {
    condition     = can(cidrhost(var.admin_cidr, 0))
    error_message = "admin_cidr must be a CIDR, such as 203.0.113.10/32."
  }
}

variable "instance_type" {
  description = "Free-tier size used for every SwiftDrop node."
  type        = string
  default     = "m7i-flex.large"
}

variable "root_volume_gb" {
  description = "Root disk for the apps and data nodes."
  type        = number
  default     = 40
}

variable "platform_node" {
  description = "Set true later to add the Grafana, Loki, Tempo, and Argo CD node."
  type        = bool
  default     = false
}

variable "platform_volume_gb" {
  description = "Root disk for the platform node. Loki and Prometheus need more space."
  type        = number
  default     = 50
}
