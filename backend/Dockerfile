# 1. Usa o Maven e o Java 25 para compilar o código
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY . .
# -DskipTests de propósito: os testes de integração usam Testcontainers (MySQL em container),
# que precisa de Docker, e não há Docker dentro do "docker build"/build da Render. Os testes
# continuam sendo COMPILADOS aqui e devem rodar com "mvn test" localmente ou em CI (GitHub Actions).
RUN mvn clean package -DskipTests

# 2. Usa uma imagem Java 25 leve para rodar a aplicação
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
# Roda como usuário sem privilégios: se a aplicação for comprometida (ex.: RCE
# via alguma dependência), o processo não tem privilégio de root dentro do container.
RUN addgroup -S smartstock && adduser -S smartstock -G smartstock
COPY --from=build /app/target/*.jar /app/app.jar
RUN chown smartstock:smartstock /app/app.jar
USER smartstock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
