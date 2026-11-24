resource "aws_security_group" "apps" {
  name        = "swiftdrop-apps"
  description = "Apps node: SSH from you, web from the internet, k3s from the data node"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "swiftdrop-apps"
  }
}

resource "aws_security_group" "data" {
  name        = "swiftdrop-data"
  description = "Data node: SSH from you, k3s from the apps node"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "swiftdrop-data"
  }
}

resource "aws_security_group" "platform" {
  count = var.platform_node ? 1 : 0

  name        = "swiftdrop-platform"
  description = "Platform node: SSH from you, k3s from the other nodes"
  vpc_id      = aws_vpc.main.id

  tags = {
    Name = "swiftdrop-platform"
  }
}

resource "aws_vpc_security_group_ingress_rule" "apps_ssh" {
  security_group_id = aws_security_group.apps.id
  description       = "SSH from the operator"
  cidr_ipv4         = var.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "apps_http" {
  security_group_id = aws_security_group.apps.id
  description       = "Ingress HTTP"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 80
  to_port           = 80
}

resource "aws_vpc_security_group_ingress_rule" "apps_https" {
  security_group_id = aws_security_group.apps.id
  description       = "Ingress HTTPS"
  cidr_ipv4         = "0.0.0.0/0"
  ip_protocol       = "tcp"
  from_port         = 443
  to_port           = 443
}

resource "aws_vpc_security_group_ingress_rule" "apps_api" {
  security_group_id            = aws_security_group.apps.id
  description                  = "k3s API from the data node"
  referenced_security_group_id = aws_security_group.data.id
  ip_protocol                  = "tcp"
  from_port                    = 6443
  to_port                      = 6443
}

resource "aws_vpc_security_group_ingress_rule" "apps_vxlan" {
  security_group_id            = aws_security_group.apps.id
  description                  = "Flannel VXLAN from the data node"
  referenced_security_group_id = aws_security_group.data.id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "apps_kubelet" {
  security_group_id            = aws_security_group.apps.id
  description                  = "kubelet from the data node"
  referenced_security_group_id = aws_security_group.data.id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_ingress_rule" "data_ssh" {
  security_group_id = aws_security_group.data.id
  description       = "SSH from the operator"
  cidr_ipv4         = var.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "data_vxlan" {
  security_group_id            = aws_security_group.data.id
  description                  = "Flannel VXLAN from the apps node"
  referenced_security_group_id = aws_security_group.apps.id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "data_kubelet" {
  security_group_id            = aws_security_group.data.id
  description                  = "kubelet from the apps node"
  referenced_security_group_id = aws_security_group.apps.id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_egress_rule" "apps_all" {
  security_group_id = aws_security_group.apps.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_egress_rule" "data_all" {
  security_group_id = aws_security_group.data.id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}

resource "aws_vpc_security_group_ingress_rule" "platform_ssh" {
  count = var.platform_node ? 1 : 0

  security_group_id = aws_security_group.platform[0].id
  description       = "SSH from the operator"
  cidr_ipv4         = var.admin_cidr
  ip_protocol       = "tcp"
  from_port         = 22
  to_port           = 22
}

resource "aws_vpc_security_group_ingress_rule" "platform_vxlan_apps" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.platform[0].id
  description                  = "Flannel VXLAN from the apps node"
  referenced_security_group_id = aws_security_group.apps.id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "platform_vxlan_data" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.platform[0].id
  description                  = "Flannel VXLAN from the data node"
  referenced_security_group_id = aws_security_group.data.id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "platform_kubelet_apps" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.platform[0].id
  description                  = "kubelet from the apps node"
  referenced_security_group_id = aws_security_group.apps.id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_ingress_rule" "platform_kubelet_data" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.platform[0].id
  description                  = "kubelet from the data node"
  referenced_security_group_id = aws_security_group.data.id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_ingress_rule" "apps_vxlan_platform" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.apps.id
  description                  = "Flannel VXLAN from the platform node"
  referenced_security_group_id = aws_security_group.platform[0].id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "apps_kubelet_platform" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.apps.id
  description                  = "kubelet from the platform node"
  referenced_security_group_id = aws_security_group.platform[0].id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_ingress_rule" "apps_api_platform" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.apps.id
  description                  = "k3s API from the platform node"
  referenced_security_group_id = aws_security_group.platform[0].id
  ip_protocol                  = "tcp"
  from_port                    = 6443
  to_port                      = 6443
}

resource "aws_vpc_security_group_ingress_rule" "data_vxlan_platform" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.data.id
  description                  = "Flannel VXLAN from the platform node"
  referenced_security_group_id = aws_security_group.platform[0].id
  ip_protocol                  = "udp"
  from_port                    = 8472
  to_port                      = 8472
}

resource "aws_vpc_security_group_ingress_rule" "data_kubelet_platform" {
  count = var.platform_node ? 1 : 0

  security_group_id            = aws_security_group.data.id
  description                  = "kubelet from the platform node"
  referenced_security_group_id = aws_security_group.platform[0].id
  ip_protocol                  = "tcp"
  from_port                    = 10250
  to_port                      = 10250
}

resource "aws_vpc_security_group_egress_rule" "platform_all" {
  count = var.platform_node ? 1 : 0

  security_group_id = aws_security_group.platform[0].id
  ip_protocol       = "-1"
  cidr_ipv4         = "0.0.0.0/0"
}
