output "apps_elastic_ip" {
  description = "Public address of the apps node. It stays the same after a stop and start."
  value       = aws_eip.apps.public_ip
}

output "data_elastic_ip" {
  description = "Public address of the data node. It stays the same after a stop and start."
  value       = aws_eip.data.public_ip
}

output "apps_private_ip" {
  description = "Private address the data node uses to join k3s."
  value       = aws_instance.apps.private_ip
}

output "data_private_ip" {
  description = "Private address of the data node."
  value       = aws_instance.data.private_ip
}

output "platform_elastic_ip" {
  description = "Public address of the platform node, when that node is enabled."
  value       = try(aws_eip.platform[0].public_ip, null)
}

output "ssh_key_path" {
  description = "Private key for SSH. It is not committed."
  value       = local_sensitive_file.ssh_key.filename
}

output "availability_zone" {
  value = local.az
}
