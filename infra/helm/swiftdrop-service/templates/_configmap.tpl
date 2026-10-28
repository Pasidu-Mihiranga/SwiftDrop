{{/*
Non-secret configuration. Every SwiftDrop service is configured through environment
variables only (12-Factor), so this is consumed with envFrom rather than mounted.
*/}}
{{- define "swiftdrop-service.configmap" -}}
{{- $data := include "swiftdrop-service.configData" . | fromYaml -}}
{{- if $data }}
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ include "swiftdrop-service.fullname" . }}
  labels:
    {{- include "swiftdrop-service.labels" . | nindent 4 }}
data:
  {{- range $key, $value := $data }}
  {{ $key }}: {{ $value | quote }}
  {{- end }}
{{- end }}
{{- end -}}
