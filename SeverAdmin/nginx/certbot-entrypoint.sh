#!/bin/sh
# 七零喵僵尸末日生存 - certbot 证书申请脚本
# 首次部署流程：
#   1. docker compose up -d          （nginx 先启动，80 端口可用）
#   2. docker compose --profile ssl run --rm certbot
#   3. docker compose restart nginx
set -e
DOMAIN="${DOMAIN:-mc.sh197.dpdns.org}"
EMAIL="${SSL_EMAIL:-admin@sh197.dpdns.org}"

echo "==> 申请证书: ${DOMAIN}"
certbot certonly \
  --webroot -w /var/www/certbot \
  -d "${DOMAIN}" \
  --email "${EMAIL}" \
  --agree-tos --no-eff-email \
  --non-interactive \
  --deploy-hook "cp -L /etc/letsencrypt/live/${DOMAIN}/fullchain.pem /etc/nginx/certs/live/${DOMAIN}/fullchain.pem 2>/dev/null || true; cp -L /etc/letsencrypt/live/${DOMAIN}/privkey.pem /etc/nginx/certs/live/${DOMAIN}/privkey.pem 2>/dev/null || true"

echo "==> 证书完成，请执行: docker compose restart nginx"
