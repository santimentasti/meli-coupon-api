# Usa una imagen base liviana de Java 17
FROM eclipse-temurin:17-jdk-alpine

# Crea un directorio de trabajo
WORKDIR /app

# Copia todo el contenido del proyecto al contenedor
COPY . .

# Da permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw

# Construye el proyecto (sin correr tests)
RUN ./mvnw clean package -DskipTests

# Expone el puerto por donde tu app escucha
EXPOSE 8080

# Comando para ejecutar la aplicación
CMD ["java", "-jar", "target/meli-coupon-api-1.0.0.jar"]
