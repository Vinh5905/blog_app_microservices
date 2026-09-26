FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY public public
COPY src src
COPY index.html vite.config.mjs ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:4714e0b1b2577eaa1a6131d07c958b67f0eb68e6d0521e90c6e5287db8cf0bc5
# CVE-2026-93990: the pinned base predates Alpine's libexpat fix.
USER root
RUN apk add --no-cache 'libexpat=2.8.5-r0'
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/build /usr/share/nginx/html
USER 101
EXPOSE 8080
