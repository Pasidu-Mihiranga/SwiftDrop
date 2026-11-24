data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]

  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }

  filter {
    name   = "virtualization-type"
    values = ["hvm"]
  }
}

resource "tls_private_key" "ssh" {
  algorithm = "RSA"
  rsa_bits  = 4096
}

resource "aws_key_pair" "ssh" {
  key_name   = "swiftdrop-deploy"
  public_key = tls_private_key.ssh.public_key_openssh
}

resource "local_sensitive_file" "ssh_key" {
  filename        = "${path.module}/swiftdrop-key.pem"
  content         = tls_private_key.ssh.private_key_pem
  file_permission = "0400"
}

resource "aws_instance" "apps" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.apps.id]
  key_name               = aws_key_pair.ssh.key_name

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
  }

  tags = {
    Name = "swiftdrop-apps"
    Role = "apps"
  }
}

resource "aws_instance" "data" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.data.id]
  key_name               = aws_key_pair.ssh.key_name

  root_block_device {
    volume_type = "gp3"
    volume_size = var.root_volume_gb
  }

  tags = {
    Name = "swiftdrop-data"
    Role = "data"
  }
}

resource "aws_instance" "platform" {
  count = var.platform_node ? 1 : 0

  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = aws_subnet.public.id
  vpc_security_group_ids = [aws_security_group.platform[0].id]
  key_name               = aws_key_pair.ssh.key_name

  root_block_device {
    volume_type = "gp3"
    volume_size = var.platform_volume_gb
  }

  tags = {
    Name = "swiftdrop-platform"
    Role = "platform"
  }
}

resource "aws_eip" "apps" {
  domain   = "vpc"
  instance = aws_instance.apps.id

  tags = {
    Name = "swiftdrop-apps"
  }
}

resource "aws_eip" "data" {
  domain   = "vpc"
  instance = aws_instance.data.id

  tags = {
    Name = "swiftdrop-data"
  }
}

resource "aws_eip" "platform" {
  count = var.platform_node ? 1 : 0

  domain   = "vpc"
  instance = aws_instance.platform[0].id

  tags = {
    Name = "swiftdrop-platform"
  }
}
