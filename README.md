# 🧾 meli-coupon-api

API REST que simula el funcionamiento de la API de cupones de Mercado Libre. Permite calcular la mejor combinación de productos que maximiza el uso de un cupón de descuento, dado un presupuesto y una lista de IDs de productos.

## 🚀 Características

- **Optimización de cupones**: Calcula la combinación óptima de productos que maximiza el uso del cupón sin exceder el presupuesto.
- **Integración con la API de Mercado Libre**: Obtiene información de productos en tiempo real desde la API pública de Mercado Libre.
- **Despliegue en Render**: Preparado para correr en entornos cloud modernos como [Render](https://render.com).
- **Contenedorización con Docker**: Incluye un `Dockerfile` para facilitar la ejecución en contenedores.

## 🛠️ Tecnologías utilizadas

- **Java 17**
- **Spring Boot**
- **Maven**
- **Docker**
- **Render (para despliegue)**

## 📦 Instalación y ejecución local

### Prerrequisitos

- Java 17
- Maven
- Docker (opcional, para ejecución en contenedor)

### Clonar el repositorio

```bash
git clone https://github.com/santimentasti/meli-coupon-api.git
cd meli-coupon-api
```

### Ejecutar con Maven

```bash
mvnw spring-boot:run
```

La API estará disponible en `http://localhost:8080`.

### Ejecutar con Docker

```bash
docker build -t meli-coupon-api .
docker run -p 8080:8080 meli-coupon-api
```

## 📚 Uso de la API

### Endpoint: `/coupon`

- **Método**: `POST`
- **Descripción**: Calcula la mejor combinación de productos que maximiza el uso del cupón sin exceder el presupuesto.

#### Solicitud

```json
{
  "item_ids": ["MLA599260060", "MLA594239600"],
  "amount": 5000
}
```

#### Respuesta

```json
{
  "item_ids": ["MLA1", "MLA3"],
  "total": 4999.99
}
```

## ☁️ Despliegue en Render

Este proyecto puede desplegarse fácilmente en [Render](https://render.com):

1. Crear un nuevo servicio de tipo **Web Service**.
2. Conectar tu repositorio de GitHub.
3. Elegir el runtime **Docker**.
4. Render detectará automáticamente el `Dockerfile` y construirá el servicio.
5. Configurar la variable `PORT` si es necesario (Render la provee automáticamente).

Actualmente esta desplegado en: https://meli-coupon-api.onrender.com

## 🧪 Pruebas

Para ejecutar las pruebas unitarias:

```bash
mvnw test
```

## 📄 Licencia

Este proyecto está bajo la Licencia MIT. Consulta el archivo [LICENSE](LICENSE) para más información.
