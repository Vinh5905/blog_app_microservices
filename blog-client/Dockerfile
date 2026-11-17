FROM node:22-alpine@sha256:0a7108bf6c7bf5de370ffb1a3ed6be93d405b43ff159f681a8d18c0e2bc2e402 AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY public public
COPY src src
COPY index.html vite.config.mjs ./
RUN npm run build

FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
# Apply security patches from the pinned base image's Alpine release.
USER root
RUN apk add --no-cache --upgrade \
        brotli-libs curl libcurl libxslt musl musl-utils zlib \
        'libexpat>=2.10.0-r0'
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/build /usr/share/nginx/html
USER 101
EXPOSE 8080
