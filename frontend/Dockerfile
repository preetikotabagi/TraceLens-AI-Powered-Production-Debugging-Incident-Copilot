# ---- Build stage -------------------------------------------------------------
# Debian (glibc) based image on purpose: Vite 8 builds with Rolldown, which ships a prebuilt native
# binary per platform as an *optional* npm dependency. On Alpine (musl) that binary was missing
# ("Cannot find module '@rolldown/binding-linux-x64-musl'"). The glibc build is the widely used
# one. Only this build stage changes; the final nginx image below stays small and Alpine-based.
FROM node:22-bookworm-slim AS build
WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .

# Vite bakes VITE_* variables into the JavaScript bundle at build time, so the backend URL
# (as seen from the user's BROWSER) must be supplied here. It is public, never a secret.
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
RUN npm run build

# ---- Runtime stage -----------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
