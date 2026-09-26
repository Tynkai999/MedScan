# ==============================================================================
# MEDSCAN ENTERPRISE — DOCKERFILE MULTI-STAGE POUR DÉPLOIEMENT EN LIGNE
# Conforme aux normes DevSecOps : image minimale, utilisateur non-root, Java 21
# ==============================================================================

# --- ÉTAPE 1 : Compilation du projet ---
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copie des descripteurs Maven pour mise en cache
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Rendre le wrapper Maven exécutable et télécharger les dépendances de base
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copie du code source
COPY src ./src

# Compilation et packaging de l'application
RUN ./mvnw clean package -DskipTests

# Extraction des dépendances nécessaires au runtime
RUN ./mvnw dependency:copy-dependencies -DoutputDirectory=target/lib -DincludeScope=provided

# --- ÉTAPE 2 : Image d'Exécution Minimale et Sécurisée ---
FROM eclipse-temurin:21-jre-alpine

LABEL maintainer="MedScan Enterprise Architecture Team"
LABEL description="Backend d'authentification et d'API MedScan Enterprise"

WORKDIR /app

# Création d'un utilisateur non-privilégié pour des raisons de sécurité
RUN addgroup -S medscan && adduser -S medscan -G medscan

# Copie des classes compilées et des bibliothèques nécessaires
COPY --from=builder /build/target/classes /app/classes
COPY --from=builder /build/target/lib /app/lib

# Définition des permissions
RUN chown -R medscan:medscan /app

USER medscan

# Port d'écoute du serveur
EXPOSE 8080

# Variables d'environnement par défaut
ENV PORT=8080
ENV JAVA_OPTS="-Xms128m -Xmx512m -XX:+UseG1GC"

# Point d'entrée : démarrage du serveur MedScan
CMD ["sh", "-c", "java $JAVA_OPTS -cp /app/classes:/app/lib/* com.medscan.server.MedscanServer"]
