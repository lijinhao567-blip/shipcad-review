FROM node:26-alpine AS build

WORKDIR /app
COPY frontend-vue/package.json frontend-vue/package-lock.json ./
RUN npm ci

COPY frontend-vue ./
RUN npm run build

FROM nginx:1.27-alpine

COPY frontend-vue/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
