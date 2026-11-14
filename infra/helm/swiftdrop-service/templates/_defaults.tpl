{{/*
Library defaults.

A library chart's values.yaml is NOT merged into the charts that depend on it (Helm
namespaces a dependency's values under its chart name), so the defaults live here as a
named template instead. "swiftdrop-service.values" layers them with the umbrella's
global.defaults and then the service's own values, which is the precedence every template
in this chart reads from.

Nothing here is a credential. Secret material reaches the pod only through "secretRefs",
which names Secrets created outside Git.
*/}}
{{- define "swiftdrop-service.defaults" -}}
image:
  repository: ""
  tag: ""
  pullPolicy: ""

replicaCount: 1

# Rolling update behaviour. The default surges one extra pod so a release never loses
# capacity. On a small cluster set maxSurge: 0 instead: doubling a JVM's footprint during
# an upgrade can starve both the old and the new pod so neither becomes ready.
strategy:
  maxSurge: 1
  maxUnavailable: 0

# Port the container listens on. The Service exposes the same number.
containerPort: 8080
service:
  type: ClusterIP
  port: 0

# Probe paths differ per runtime: Spring Boot exposes the actuator health groups,
# the Node and Python services expose /health/live and /health/ready.
probes:
  liveness:
    enabled: true
    path: /actuator/health/liveness
    initialDelaySeconds: 20
    periodSeconds: 10
    timeoutSeconds: 3
    failureThreshold: 6
  readiness:
    enabled: true
    path: /actuator/health/readiness
    initialDelaySeconds: 10
    periodSeconds: 5
    timeoutSeconds: 3
    failureThreshold: 6
  startup:
    enabled: true
    path: /actuator/health/readiness
    periodSeconds: 5
    timeoutSeconds: 3
    failureThreshold: 40

# Non-secret environment, rendered into a ConfigMap. Merged over global.commonConfig.
config: {}
# Extra env entries for values that are not plain strings (valueFrom, fieldRef).
extraEnv: []
# Secrets mounted as envFrom. Created with kubectl, never templated from Git.
secretRefs: []

# Prometheus scrape annotations. Java services serve /actuator/prometheus, the others /metrics.
metrics:
  enabled: true
  path: /actuator/prometheus

podAnnotations: {}
podLabels: {}

# Matches the Dockerfiles: the Java and Python images run as UID 10001, the Node image as "node" (1000).
podSecurityContext:
  runAsNonRoot: true
  runAsUser: 10001
  runAsGroup: 10001
  fsGroup: 10001
  seccompProfile:
    type: RuntimeDefault
containerSecurityContext:
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: false
  capabilities:
    drop: ["ALL"]

resources:
  requests:
    cpu: 50m
    memory: 256Mi
  limits:
    memory: 512Mi

autoscaling:
  enabled: false
  minReplicas: 2
  maxReplicas: 5
  targetCPUUtilizationPercentage: 70

nodeSelector: {}
tolerations: []
affinity: {}

terminationGracePeriodSeconds: 40

nameOverride: ""
fullnameOverride: ""
{{- end -}}

{{/*
Effective values: library defaults, then the umbrella's global.defaults, then the
service's own values. Every template in this chart uses this rather than .Values directly
so a thin chart only has to declare what differs.
*/}}
{{- define "swiftdrop-service.values" -}}
{{- $defaults := include "swiftdrop-service.defaults" . | fromYaml -}}
{{- $globals := (.Values.global | default dict).defaults | default dict -}}
{{- $own := omit .Values "global" -}}
{{- toYaml (mergeOverwrite $defaults (deepCopy $globals) (deepCopy $own)) -}}
{{- end -}}
