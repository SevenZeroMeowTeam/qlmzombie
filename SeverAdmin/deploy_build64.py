# -*- coding: utf-8 -*-
"""部署 build64（CdInfectionGuard mixin 修复）：上传 jar + 同步 mods + 重启"""
import paramiko, os

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=60)

local_jar = r'd:\mcmod\build\libs\qlmzombie-3.0.0.beta.build64.jar'
remote_jar = '/www/wwwroot/build/libs/qlmzombie-3.0.0.beta.build64.jar'

def run(cmd, timeout=900):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    return stdout.read().decode('utf-8', errors='replace')

print('========== 1. 上传 build64 jar ==========')
sftp = c.open_sftp()
sftp.put(local_jar, remote_jar, callback=lambda sent, total: None)
print('jar 上传完成:', os.path.getsize(local_jar), 'bytes')
sftp.close()

print('========== 2. 复制 build64 到 mc/mods + 清理残留 ==========')
print(run(r'''
# 清理旧的 build63/62 主 jar（保留最新 build64）
rm -f /www/wwwroot/minecraftsc/mc/mods/qlmzombie-*.jar
cp -f /www/wwwroot/build/libs/qlmzombie-3.0.0.beta.build64.jar /www/wwwroot/minecraftsc/mc/mods/
ls -la /www/wwwroot/minecraftsc/mc/mods/qlmzombie*.jar
echo '--- player 残留 ---'
ls /www/wwwroot/minecraftsc/mc/mods/ | grep -iE 'playerengine|player2npc' || echo "无残留 OK"
'''))

print('========== 3. 重启 minecraft 容器 ==========')
print(run('cd /www/wwwroot/minecraftsc && docker compose restart minecraft 2>&1'))

print('========== 4. 等待 Done ==========')
print(run(r'''
for i in $(seq 1 50); do
  if docker exec qlm-minecraft sh -c 'grep -q "Done (" /data/logs/latest.log 2>/dev/null'; then
    echo "已启动（第 ${i} 次检查）"
    break
  fi
  sleep 15
done
docker exec qlm-minecraft grep -E 'Done \(' /data/logs/latest.log | tail -1
''', timeout=900))

print('========== 5. 验证 ==========')
print(run(r'''
echo '--- 容器状态 ---'
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo '--- 加载的 jar ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- 版本日志 ---'
docker exec qlm-minecraft sh -c 'grep -E "build64|已加载|CdInfectionGuard|MixinLivingEntity" /data/logs/latest.log | head -8'
echo '--- mixin 应用检查 ---'
docker exec qlm-minecraft sh -c 'grep -iE "qlmzombie.mixins.json|MixinLivingEntity|Applied.*Mixin" /data/logs/latest.log | head -5'
echo '--- 握手 ---'
docker exec qlm-minecraft sh -c 'mc-monitor status --host localhost --port 25565 2>&1'
''', timeout=180))

c.close()
