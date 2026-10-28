{{/*
Naming, labelling and image helpers. Every resource the library renders goes through
these so a workload is identifiable by the standard Kubernetes labels.
*/}}

{{- define "swiftdrop-service.name" -}}
{{- default .Chart.Name (include "swiftdrop-service.values" . | fromYaml).nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{/*
Full name. The chart name is already the service name (pricing-service), so the release
name is deliberately not prefixed: in-cluster DNS then matches the service names that
application.yml defaults and docker-compose already use (http://pricing-service:8083).
*/}}
{{- define "swiftdrop-service.fullname" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- if $v.fullnameOverride -}}
{{- $v.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- include "swiftdrop-service.name" . | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "swiftdrop-service.labels" -}}
app.kubernetes.io/name: {{ include "swiftdrop-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: swiftdrop
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end -}}

{{- define "swiftdrop-service.selectorLabels" -}}
app.kubernetes.io/name: {{ include "swiftdrop-service.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Image reference. global.imageRegistry retags the whole platform at once and
global.imageTag is the shared default tag, so a release sets both in one place.
*/}}
{{- define "swiftdrop-service.image" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- $g := .Values.global | default dict -}}
{{- $registry := $g.imageRegistry | default "" -}}
{{- $repository := required (printf "image.repository is required for chart %s" .Chart.Name) $v.image.repository -}}
{{- $tag := $v.image.tag | default $g.imageTag | default .Chart.AppVersion | default "latest" -}}
{{- if $registry -}}
{{- printf "%s/%s:%s" (trimSuffix "/" $registry) $repository $tag -}}
{{- else -}}
{{- printf "%s:%s" $repository $tag -}}
{{- end -}}
{{- end -}}

{{- define "swiftdrop-service.imagePullPolicy" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- $g := .Values.global | default dict -}}
{{- $v.image.pullPolicy | default $g.imagePullPolicy | default "IfNotPresent" -}}
{{- end -}}

{{/*
Service port, defaulting to the container port so a thin chart declares the port once.
*/}}
{{- define "swiftdrop-service.servicePort" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- if and $v.service.port (ne (int $v.service.port) 0) -}}
{{- int $v.service.port -}}
{{- else -}}
{{- int $v.containerPort -}}
{{- end -}}
{{- end -}}

{{/*
Non-secret environment: the platform-wide values from the umbrella (global.commonConfig)
with the service's own config layered on top, plus PORT derived from the container port so
the app and the probe can never disagree about which port it listens on.
*/}}
{{- define "swiftdrop-service.configData" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
{{- $shared := (.Values.global | default dict).commonConfig | default dict -}}
{{- $derived := dict "PORT" (printf "%d" (int $v.containerPort)) -}}
{{- $own := $v.config | default dict -}}
{{- toYaml (mergeOverwrite $derived (deepCopy $shared) (deepCopy $own)) -}}
{{- end -}}
