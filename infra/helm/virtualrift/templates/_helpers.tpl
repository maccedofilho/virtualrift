{{- define "virtualrift.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "virtualrift.componentFullname" -}}
{{- $root := .root -}}
{{- $name := .name -}}
{{- printf "%s-%s" $root.Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "virtualrift.selectorLabels" -}}
app.kubernetes.io/instance: {{ .root.Release.Name }}
app.kubernetes.io/component: {{ .name }}
{{- end -}}

{{- define "virtualrift.commonLabels" -}}
helm.sh/chart: {{ include "virtualrift.chart" .root }}
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
app.kubernetes.io/part-of: virtualrift
{{ include "virtualrift.selectorLabels" . }}
{{- end -}}

{{- define "virtualrift.serviceAccountName" -}}
{{- if .Values.global.serviceAccount.create -}}
{{- default (printf "%s-runtime" .Release.Name) .Values.global.serviceAccount.name -}}
{{- else -}}
{{- required "global.serviceAccount.name must be set when serviceAccount.create is false" .Values.global.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{- define "virtualrift.renderEnv" -}}
{{- $root := .root -}}
{{- with .env }}
{{- range $key, $value := . }}
- name: {{ $key }}
  value: {{ tpl ($value | toString) $root | quote }}
{{- end }}
{{- end }}
{{- with .secretEnv }}
{{- range $key, $value := . }}
- name: {{ $key }}
  valueFrom:
    secretKeyRef:
      name: {{ tpl ($value.secretName | toString) $root | quote }}
      key: {{ $value.key | quote }}
      {{- if hasKey $value "optional" }}
      optional: {{ $value.optional }}
      {{- end }}
{{- end }}
{{- end }}
{{- end -}}

{{- define "virtualrift.renderEnvFrom" -}}
{{- $root := .root -}}
{{- with .envFrom }}
{{- range .configMaps }}
- configMapRef:
    name: {{ tpl (. | toString) $root | quote }}
{{- end }}
{{- range .secrets }}
- secretRef:
    name: {{ tpl (. | toString) $root | quote }}
{{- end }}
{{- end }}
{{- end -}}

{{- define "virtualrift.renderProbe" -}}
{{- $probe := . -}}
{{- if eq (default "http" $probe.type) "http" }}
httpGet:
  path: {{ $probe.path | quote }}
  port: {{ $probe.port }}
{{- else if eq $probe.type "tcp" }}
tcpSocket:
  port: {{ $probe.port }}
{{- end }}
initialDelaySeconds: {{ default 10 $probe.initialDelaySeconds }}
periodSeconds: {{ default 10 $probe.periodSeconds }}
timeoutSeconds: {{ default 2 $probe.timeoutSeconds }}
successThreshold: {{ default 1 $probe.successThreshold }}
failureThreshold: {{ default 6 $probe.failureThreshold }}
{{- end -}}
