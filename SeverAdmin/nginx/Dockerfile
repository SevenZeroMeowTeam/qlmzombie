# 七零喵僵尸末日生存 - Nginx 镜像
FROM nginx:1.25-alpine
COPY nginx.conf /etc/nginx/nginx.conf
COPY nginx-stream.conf /etc/nginx/conf.d/stream.conf
COPY stream-extra.conf /etc/nginx/conf.d/stream-extra.conf
COPY nginx-http.conf /etc/nginx/conf.d/http.conf
COPY www/ /var/www/certbot/
RUN apk add --no-cache wget
EXPOSE 80 443 25565 25566 25567 25568
