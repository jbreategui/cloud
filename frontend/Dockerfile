# Este contenedor solo construye el frontend Vite y copia el resultado a un
# volumen compartido con el servicio nginx (ver docker-compose.yml). No sirve
# nada por si mismo: termina y sale con exit code 0 cuando copia el build.
FROM node:20-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Se hornea en build time: en produccion queda vacio para que el navegador
# llame a rutas relativas (/api/...) que el nginx reverse-proxy resuelve.
ARG VITE_API_URL=""
ENV VITE_API_URL=$VITE_API_URL

RUN npm run build

CMD ["sh", "-c", "cp -r /app/dist/. /dist_output/"]
