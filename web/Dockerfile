FROM node:22-alpine AS build

WORKDIR /workspace/web

COPY web/package.json web/package-lock.json ./
RUN npm ci

COPY web/index.html web/tsconfig.json web/vite.config.ts ./
COPY web/src src
# 帮助页以 ?raw 引入 docs/*.md，构建时还需复制 docs/assets 截图
COPY docs /workspace/docs
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.27-alpine

COPY web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/web/dist /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=5s --start-period=10s --retries=5 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
