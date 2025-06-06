# 🎟️ Meli Coupon API

API para optimizar cupones de descuento en MercadoLibre, encuentra la mejor combinación de productos que maximice el uso del cupón sin exceder el presupuesto.

## 🚀 URL Base

```
https://meli-coupon-api.onrender.com
```

## 🔐 Endpoints de Autenticación

### GET `/coupon/api/mercadolibre/auth/authorization-url`

Obtiene la URL para autorizar la aplicación en MercadoLibre.

### GET `/coupon/api/mercadolibre/auth/callback`

Callback automático que procesa la autorización (no llamar manualmente).

### GET `/coupon/api/mercadolibre/auth/current-token`

Devuelve el token de acceso actual si está disponible.

### POST `/coupon/api/mercadolibre/auth/refresh-token`

Renueva un token expirado usando el refresh token.

## 📋 Cómo probar la API

### Opción rápida (si ya hay token activo)
Puedes ir directamente al Paso 3 si alguien ya configuró la autenticación.

### Paso 1: Verificar que la API esté activa

**GET** `/health`

```
curl -X GET "https://meli-coupon-api.onrender.com/health"
```

**Respuesta esperada:**
```json
{
    "status": "UP",
    "timestamp": "2024-06-06T19:17:32.123Z"
}
```

### Paso 2: Configurar autenticación con MercadoLibre

⚠️ **Importante**: Para usar la API, primero necesitas autenticarte con MercadoLibre.

#### Opción A: Verificar si ya hay un token activo

**GET** `/coupon/api/mercadolibre/auth/current-token`
```
curl -X GET "https://meli-coupon-api.onrender.com/coupon/api/mercadolibre/auth/current-token"
```

**Si hay token activo:**
```json
{
    "access_token": "APP_USR-...",
    "token_type": "Bearer",
    "expires_in": 21600,
    "user_id": 313179164,
    "refresh_token": "TG-..."
}
```

#### Opción B: Generar nuevo token (si es necesario)

1. **Obtener URL de autorización:**
```
curl -X GET "https://meli-coupon-api.onrender.com/coupon/api/mercadolibre/auth/authorization-url"
```

**Respuesta:**
```json
{
    "authorizationUrl": "https://auth.mercadolibre.com.ar/authorization?response_type=code&client_id=..."
}
```

2. **Pegar la URL** en el navegador y autorizar la aplicación

3. **El callback se procesará automáticamente** y el token estará disponible

#### Verificar estado de autenticación

```
curl -X GET "https://meli-coupon-api.onrender.com/coupon/api/mercadolibre/auth/current-token"
```

### Paso 3: Calcular cupón óptimo

**POST** `/coupon`

#### Headers requeridos:
```
Content-Type: application/json
```

#### Body (JSON):
```json
{
    "item_ids": ["MLA1488600299", "MLA877517533"],
    "amount": 350000.00
}
```

#### Ejemplo completo con cURL:
```
curl -X POST "https://meli-coupon-api.onrender.com/coupon" \
  -H "Content-Type: application/json" \
  -d '{
    "item_ids": ["MLA1488600299", "MLA877517533"],
    "amount": 350000.00
  }'
```

#### Respuesta exitosa:
```json
{
    "total": 221999,
    "item_ids": [
        "MLA1488600299",
        "MLA877517533"
    ]
}
```

## 🧪 Items de prueba

Aquí tienes varios conjuntos de items para probar diferentes scenarios:

### Escenario 1: Presupuesto alto, pocos items
```json
{
    "item_ids": ["MLA1488600299", "MLA877517533"],
    "amount": 350000.00
}
```
*Resultado esperado: Ambos items (total ~$221,999)*

### Escenario 2: Presupuesto limitado
```json
{
    "item_ids": ["MLA1488600299", "MLA877517533"],
    "amount": 100000.00
}
```
*Resultado esperado: Solo el item más barato (MLA877517533)*

### Escenario 3: Presupuesto muy bajo
```json
{
    "item_ids": ["MLA1488600299", "MLA877517533"],
    "amount": 10000.00
}
```
*Resultado esperado: Lista vacía (ningún item cabe en el presupuesto)*

## 📱 Probar con Postman

### Configuración:
1. **Método**: `POST`
2. **URL**: `https://meli-coupon-api.onrender.com/coupon`
3. **Headers**: 
   - `Content-Type: application/json`
4. **Body**: 
   - Seleccionar `raw`
   - Seleccionar `JSON`
   - Pegar uno de los ejemplos de arriba

### Captura de pantalla esperada:
- Status: `200 OK`
- Response time: < 5 segundos
- Body: JSON con `total` e `item_ids`

## 🔧 Troubleshooting

### Error 401 - No autorizado
```json
{
    "error": "Access token no disponible"
}
```
**Solución**: 
1. Verificar si hay token activo: `GET /coupon/api/mercadolibre/auth/current-token`
2. Si no hay token o expiró, seguir el proceso de autenticación del Paso 2
3. Los tokens expiran cada 6 horas (21600 segundos)

### Error 500 - Error interno
```json
{
    "error": "Error interno del servidor"
}
```
**Posibles causas**:
- Items inexistentes en MercadoLibre
- Problemas de conectividad con la API de MercadoLibre
- Presupuesto extremadamente alto (> $1,000,000)

### Error de timeout
**Solución**: La API puede tardar hasta 15 segundos en casos complejos. Reintentar la solicitud.

### Response vacío
```json
{
    "total": 0,
    "item_ids": []
}
```
**Significa**: Ningún item cabe en el presupuesto especificado.

## 🏗️ Estructura de la respuesta

### Respuesta exitosa:
```json
{
    "total": 221999,           // Monto total gastado
    "item_ids": [              // Items seleccionados
        "MLA1488600299",
        "MLA877517533"
    ]
}
```

### Respuesta de error:
```json
{
    "error": "Descripción del error",
    "timestamp": "2024-06-06T19:17:32.123Z",
    "status": 400
}
```

## 📊 Algoritmo

La API utiliza un algoritmo híbrido:
- **Programación Dinámica**: Para casos pequeños (< 100 items, presupuesto < $10,000)
- **Greedy Optimizado**: Para casos grandes (múltiples estrategias)
- **Fallback automático**: Si hay problemas de memoria

## 📈 Límites y consideraciones

- **Máximo items por request**: 1000
- **Presupuesto máximo recomendado**: $1,000,000
- **Timeout**: 30 segundos
- **Rate limiting**: 100 requests/minuto
