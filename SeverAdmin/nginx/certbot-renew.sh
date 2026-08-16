#!/bin/sh
# 七零喵僵尸末日生存 - certbot 续期脚本（证书到期前 30 天运行）
# docker compose --profile ssl run --rm certbot-renew
set -e
DOMAIN="${DOMAIN:-mc.sh197.dpdns.org}"
EMAIL="${SSL_EMAIL:-admin@sh197.dpdns.org}"

echo "==> 续期证书: ${DOMAIN}"
certbot renew \
  --webroot -w /var/www/certbot \
  --email "${EMAIL}" \
  --agree-tos --no-eff-email \
  --non-interactive \
  --deploy-hook "cp -L /etc/letsencrypt/live/${DOMAIN}/fullchain.pem /etc/nginx/certs/live/${DOMAIN}/fullchain.pem 2>/dev/null || true; cp -L /etc/letsencrypt/live/${DOMAIN}/privkey.pem /etc/nginx/certs/live/${DOMAIN}/privkey.pem 2>/dev/null || true"

echo "==> 续期完成，请执行: docker compose restart nginx"
