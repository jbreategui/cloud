# Thaki — Galería de imágenes en AWS

Aplicación web tipo Pinterest: los usuarios suben imágenes (JPEG, PNG, WEBP), las ven en un tablero tipo *masonry* y les dan "like". El proyecto está pensado como práctica de arquitectura cloud en AWS: frontend estático en CDN, API detrás de un balanceador con varias instancias y datos repartidos entre servicios gestionados según su naturaleza.

- Frontend: https://thaki.site
- API: https://api.thaki.site

---

## 1. De qué trata (visión de negocio)

| Aspecto | Descripción |
|---|---|
| Problema | Compartir imágenes de forma rápida y visual, sin fricción (sin registro). |
| Usuario | Cualquier visitante: sube una imagen, explora la galería y reacciona con likes. |
| Propuesta de valor | Tablero visual, rápido (CDN + carga diferida) y siempre disponible (varias instancias detrás de un ALB). |
| Funcionalidades | Subir imagen · Listar galería (más recientes primero) · Dar like (botón o doble clic) · Ver qué servidor atendió la petición. |
| Métrica clave | Número de imágenes subidas y likes por imagen. |

Además de producto, el proyecto sirve para demostrar conceptos de cloud: alta disponibilidad, balanceo de carga, separación frontend/backend, gestión de secretos y elección del almacén de datos adecuado para cada tipo de dato.

---

## 2. Arquitectura

```
                    ┌──────────────────────────────┐
 Navegador ───────► │ CloudFront  (thaki.site)     │ ──► S3 (build de React)
     │              └──────────────────────────────┘
     │
     │  HTTPS       ┌──────────────────────────────┐
     └────────────► │ ALB  (api.thaki.site :443)   │
                    └──────────────┬───────────────┘
                                   │ Target Group (health check: GET /api/server)
                     ┌─────────────┴─────────────┐
                     ▼                           ▼
              EC2 #1 (APP1)               EC2 #2 (APP2)
              Docker: Spring Boot :8080   Docker: Spring Boot :8080
                     │                           │
                     └─────────────┬─────────────┘
          ┌───────────────┬────────┴──────┬───────────────────┐
          ▼               ▼               ▼                   ▼
   S3 bucket-jbrr    Postgres 17     DynamoDB           Secrets Manager
   (archivos de      (EC2 privada,   image_likes        sm_posgress
    imagen)           metadatos)     (contador likes)   (credenciales BD)
```

Diagrama editable: [`docs/Diagrama sin título.drawio.xml`](docs/) · imagen: [`docs/img.png`](docs/img.png)

### Por qué cada servicio

| Dato / necesidad | Servicio | Motivo |
|---|---|---|
| Archivos binarios | **S3** | Almacenamiento barato, durable e ilimitado; se sirve por URL directa. |
| Metadatos de imagen (nombre, tipo, tamaño, fecha) | **PostgreSQL** | Datos estructurados y consultas ordenadas; garantiza unicidad del `object_key`. |
| Likes | **DynamoDB** | Contador atómico (`ADD likes :1`) de alta concurrencia sin bloqueos en la BD relacional. |
| Credenciales de la BD | **Secrets Manager** | Nada sensible en el código ni en `.env`; la EC2 lo lee con su IAM Role. |
| Frontend | **S3 + CloudFront** | Sitio estático en CDN, HTTPS y baja latencia. |
| API | **ALB + 2 EC2** | Alta disponibilidad y balanceo; `/api/server` muestra qué instancia respondió. |

### Flujos principales

**Subir imagen** — `POST /api/images`
1. Valida que el archivo no esté vacío y que el tipo sea JPEG/PNG/WEBP.
2. Lo sube a S3 con la key `images/<uuid>.<ext>`.
3. Registra los metadatos en Postgres. Si el INSERT falla, borra el objeto de S3 (compensación).

**Listar galería** — `GET /api/images`
1. Lee los metadatos de Postgres ordenados por fecha.
2. Obtiene los likes de DynamoDB en lotes de 100 (`BatchGetItem`).
3. Devuelve cada imagen con su URL pública de S3 y su número de likes.

**Dar like** — `POST /api/images/{filename}/like`
1. Verifica en Postgres que la imagen existe (si no, 404).
2. Incrementa atómicamente el contador en DynamoDB y devuelve el total.
3. El frontend actualiza el contador de forma optimista y revierte si la petición falla.

---

## 3. Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Java 17, Spring Boot 4.1 (Web MVC), MyBatis 3, AWS SDK v2 (S3, DynamoDB, Secrets Manager), HikariCP |
| Frontend | React 19, Vite 8, Tailwind CSS 4, axios, react-router 7, react-masonry-css |
| Base de datos | PostgreSQL 17 (Docker en EC2 dedicada) |
| Contenedores | Docker multi-stage (Temurin 17 JDK → JRE), docker-compose |
| Infraestructura | CloudFront, S3, ALB, EC2, DynamoDB, Secrets Manager, IAM Roles — región `us-east-2` |

---

## 4. Estructura del repositorio

```
├── src/main/java/com/cloud/app
│   ├── config/        # Clientes AWS, DataSource (Secrets Manager), MyBatis, CORS
│   ├── controller/    # ImageController, ServerInfoController, HelloController
│   ├── service/       # ImageStorageService (S3 + Postgres), LikeService (DynamoDB)
│   ├── mapper/        # ImageMapper (MyBatis) + resources/mapper/ImageMapper.xml
│   ├── model/         # ImageRecord
│   └── exception/     # Manejo global de errores (400, 404, 500)
├── src/main/resources # application*.properties (perfiles dev/prod), schema.sql
├── frontend/          # SPA React (galería y subida)
├── docs/              # Diagrama, esquema SQL, compose de Postgres, script de DynamoDB
├── Dockerfile         # Imagen del backend
├── docker-compose.yml # Backend en cada EC2 de aplicación
└── postman_collection.json
```

---

## 5. API

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/images` | Sube una imagen (`multipart/form-data`, campo `file`). Responde `201`. |
| `GET` | `/api/images` | Lista imágenes con URL y likes. |
| `POST` | `/api/images/{filename}/like` | Suma un like. `404` si la imagen no existe. |
| `GET` | `/api/server` | Nombre de la instancia (`APP1`/`APP2`). Health check del ALB. |
| `GET` | `/api/hello` | Endpoint de prueba. |

Colección lista para usar en [`postman_collection.json`](postman_collection.json).

---

## 6. Configuración y despliegue

### Perfiles

| Perfil | Base de datos | Esquema | CORS por defecto |
|---|---|---|---|
| `dev` | Postgres local vía `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | Se crea al arrancar | `http://localhost:5173` |
| `prod` | Credenciales desde Secrets Manager (`sm_posgress`) | Se aplica a mano (`docs/schema.sql`) | `https://thaki.site` |

### Backend en cada EC2 de aplicación

```bash
cp .env.example .env      # SPRING_PROFILES_ACTIVE=prod, APP_SERVER_NAME=APP1 / APP2
docker compose up -d --build
```

La EC2 necesita un IAM Role con permisos sobre S3 (`bucket-jbrr`), DynamoDB (`image_likes`) y `secretsmanager:GetSecretValue` sobre `sm_posgress`.

### Base de datos (EC2 dedicada)

Ver instrucciones en [`docs/db/docker-compose.yml`](docs/db/docker-compose.yml). El puerto 5432 debe abrirse solo al security group de las EC2 de la app.

### Tabla de likes (una sola vez)

```bash
bash docs/dynamodb-create-table.sh
```

### Frontend

```bash
cd frontend
npm install
npm run dev     # local, usa VITE_API_URL de .env (http://localhost:8080)
npm run build   # producción, usa .env.production (https://api.thaki.site)
```

El contenido de `frontend/dist/` se sube al bucket del sitio servido por CloudFront.

---

## 7. Estado actual y mejoras pendientes

Puntos fuertes:
- Sin credenciales en código: IAM Roles + Secrets Manager.
- Compensación S3 ↔ Postgres al subir.
- Likes atómicos y escalables en DynamoDB.
- Separación clara frontend (CDN) / backend (ALB + múltiples instancias).

Mejoras identificadas:
- **Seguridad:** la API no tiene autenticación ni límite de peticiones; el límite de subida es 100 MB; el tipo de archivo se valida solo por el header `Content-Type`; el bucket de imágenes es público (convendría servirlo por CloudFront con OAC); el contenedor corre como root.
- **Robustez:** si falla la compensación en S3 queda un objeto huérfano; DynamoDB reutiliza la propiedad de región de S3.
- **Escalabilidad:** `GET /api/images` no está paginado.
- **Mantenimiento:** código sin usar (URLs prefirmadas, `HelloController`), propiedades `mybatis.*` sin efecto por la configuración manual de MyBatis, sin migraciones versionadas (Flyway/Liquibase), el build de Docker omite los tests.
