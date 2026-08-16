# 七零喵僵尸末日生存 - SeverAdmin Web 后台镜像
FROM node:20-alpine
WORKDIR /app

# 依赖（利用 Docker 层缓存）
COPY package.json ./
RUN npm install --omit=dev --registry=https://registry.npmmirror.com || npm install --omit=dev

# 应用代码 + 前端页面
COPY server.js ./
COPY public ./public

# 下载中心目录（挂载宿主机 volume 到此处）
RUN mkdir -p /app/downloads
VOLUME ["/app/downloads"]

EXPOSE 3000
CMD ["node", "server.js"]
