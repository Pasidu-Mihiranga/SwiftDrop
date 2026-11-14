{{/*
Everything a SwiftDrop workload needs. A thin service chart holds one template file whose
whole body is an include of this, so adding a shared capability means editing the library
once rather than twelve charts.
*/}}
{{- define "swiftdrop-service.all" -}}
{{ include "swiftdrop-service.configmap" . }}
{{ include "swiftdrop-service.deployment" . }}
{{ include "swiftdrop-service.service" . }}
{{ include "swiftdrop-service.hpa" . }}
{{- end -}}
