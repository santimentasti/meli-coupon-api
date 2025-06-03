#!/bin/bash

# 🚀 Script para compilar y desplegar en Google App Engine

# Salir si hay errores
set -e

# Definiciones
PROJECT_ID="simple-meli-api-398572"
REGION="uc"  # us-central = uc

echo "🚧 Compilando con Maven..."
mvn clean package -DskipTests

echo "🚀 Desplegando en Google App Engine..."
gcloud config set project $PROJECT_ID
gcloud app deploy --quiet

echo "✅ Despliegue completo. Probá tu API con:"
echo ""
echo "curl -X POST https://${PROJECT_ID}.${REGION}.r.appspot.com/coupon \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"item_ids\": [\"MLA1\", \"MLA2\", \"MLA4\"], \"amount\": 500}'"
echo ""