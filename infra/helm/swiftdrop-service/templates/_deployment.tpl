{{/*
Deployment, shared by every SwiftDrop workload: non-root container, environment from the
ConfigMap plus externally created Secrets, the three probe kinds, and a grace period long
enough for the services' "server.shutdown: graceful".
*/}}
{{- define "swiftdrop-service.deployment" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ include "swiftdrop-service.fullname" . }}
  labels:
    {{- include "swiftdrop-service.labels" . | nindent 4 }}
spec:
  {{- if not $v.autoscaling.enabled }}
  replicas: {{ $v.replicaCount }}
  {{- end }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: {{ $v.strategy.maxSurge }}
      maxUnavailable: {{ $v.strategy.maxUnavailable }}
  selector:
    matchLabels:
      {{- include "swiftdrop-service.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      annotations:
        {{- /* Roll the pods when the non-secret configuration changes. */}}
        checksum/config: {{ include "swiftdrop-service.configData" . | sha256sum }}
        {{- if $v.metrics.enabled }}
        prometheus.io/scrape: "true"
        prometheus.io/path: {{ $v.metrics.path | quote }}
        prometheus.io/port: {{ $v.containerPort | quote }}
        {{- end }}
        {{- with $v.podAnnotations }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
      labels:
        {{- include "swiftdrop-service.labels" . | nindent 8 }}
        {{- with $v.podLabels }}
        {{- toYaml . | nindent 8 }}
        {{- end }}
    spec:
      {{- with $v.podSecurityContext }}
      securityContext:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      terminationGracePeriodSeconds: {{ $v.terminationGracePeriodSeconds }}
      containers:
        - name: {{ .Chart.Name }}
          image: {{ include "swiftdrop-service.image" . }}
          imagePullPolicy: {{ include "swiftdrop-service.imagePullPolicy" . }}
          {{- with $v.containerSecurityContext }}
          securityContext:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          ports:
            - name: http
              containerPort: {{ $v.containerPort }}
              protocol: TCP
          envFrom:
            - configMapRef:
                name: {{ include "swiftdrop-service.fullname" . }}
            {{- range $v.secretRefs }}
            - secretRef:
                name: {{ . }}
            {{- end }}
          {{- with $v.extraEnv }}
          env:
            {{- toYaml . | nindent 12 }}
          {{- end }}
          {{- if $v.probes.startup.enabled }}
          startupProbe:
            httpGet:
              path: {{ $v.probes.startup.path }}
              port: http
            periodSeconds: {{ $v.probes.startup.periodSeconds }}
            timeoutSeconds: {{ $v.probes.startup.timeoutSeconds }}
            failureThreshold: {{ $v.probes.startup.failureThreshold }}
          {{- end }}
          {{- if $v.probes.liveness.enabled }}
          livenessProbe:
            httpGet:
              path: {{ $v.probes.liveness.path }}
              port: http
            initialDelaySeconds: {{ $v.probes.liveness.initialDelaySeconds }}
            periodSeconds: {{ $v.probes.liveness.periodSeconds }}
            timeoutSeconds: {{ $v.probes.liveness.timeoutSeconds }}
            failureThreshold: {{ $v.probes.liveness.failureThreshold }}
          {{- end }}
          {{- if $v.probes.readiness.enabled }}
          readinessProbe:
            httpGet:
              path: {{ $v.probes.readiness.path }}
              port: http
            initialDelaySeconds: {{ $v.probes.readiness.initialDelaySeconds }}
            periodSeconds: {{ $v.probes.readiness.periodSeconds }}
            timeoutSeconds: {{ $v.probes.readiness.timeoutSeconds }}
            failureThreshold: {{ $v.probes.readiness.failureThreshold }}
          {{- end }}
          {{- with $v.resources }}
          resources:
            {{- toYaml . | nindent 12 }}
          {{- end }}
      {{- with $v.nodeSelector }}
      nodeSelector:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with $v.tolerations }}
      tolerations:
        {{- toYaml . | nindent 8 }}
      {{- end }}
      {{- with $v.affinity }}
      affinity:
        {{- toYaml . | nindent 8 }}
      {{- end }}
{{- end -}}
