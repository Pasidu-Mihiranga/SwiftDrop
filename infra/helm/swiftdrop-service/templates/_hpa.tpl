{{/*
Optional HPA. Only for stateless request handling: a service that owns a Kafka consumer
group cannot usefully scale past its partition count.
*/}}
{{- define "swiftdrop-service.hpa" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- if $v.autoscaling.enabled }}
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: {{ include "swiftdrop-service.fullname" . }}
  labels:
    {{- include "swiftdrop-service.labels" . | nindent 4 }}
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: {{ include "swiftdrop-service.fullname" . }}
  minReplicas: {{ $v.autoscaling.minReplicas }}
  maxReplicas: {{ $v.autoscaling.maxReplicas }}
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: {{ $v.autoscaling.targetCPUUtilizationPercentage }}
{{- end }}
{{- end -}}
