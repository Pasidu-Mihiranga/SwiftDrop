{{- define "swiftdrop-service.service" -}}
{{- $v := include "swiftdrop-service.values" . | fromYaml -}}
---
apiVersion: v1
kind: Service
metadata:
  name: {{ include "swiftdrop-service.fullname" . }}
  labels:
    {{- include "swiftdrop-service.labels" . | nindent 4 }}
spec:
  type: {{ $v.service.type | default "ClusterIP" }}
  selector:
    {{- include "swiftdrop-service.selectorLabels" . | nindent 4 }}
  ports:
    - name: http
      port: {{ include "swiftdrop-service.servicePort" . }}
      targetPort: http
      protocol: TCP
{{- end -}}
