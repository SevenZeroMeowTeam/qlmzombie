# -*- coding: utf-8 -*-
"""等待 build64 完全启动并验证实际加载 jar + mixin 应用"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

def run(cmd, timeout=300):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    return stdout.read().decode('utf-8', errors='replace')

print('=== 1. 清理 mc/mods 旧 build63（只留 build64）===')
print(run(r'''
rm -f /www/wwwroot/minecraftsc/mc/mods/qlmzombie-3.0.0.beta.build63.jar
ls /www/wwwroot/minecraftsc/mc/mods/qlmzombie*.jar
'''))

print('=== 2. 重启容器 ===')
print(run('cd /www/wwwroot/minecraftsc && docker compose restart minecraft 2>&1'))

print('=== 3. 等待新 Done ===')
print(run(r'''
for i in $(seq 1 60); do
  done_line=$(docker exec qlm-minecraft sh -c 'grep "Done (" /data/logs/latest.log 2>/dev/null | tail -1')
  if echo "$done_line" | grep -q "10:"; then
    echo "新启动完成（第 ${i} 次检查）: $done_line"
    break
  fi
  sleep 15
done
''', timeout=900))

print('=== 4. 验证加载 jar + mixin ===')
print(run(r'''
echo '--- 容器内 mods qlmzombie ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- 版本日志（新段）---'
docker exec qlm-minecraft sh -c 'grep -E "build64|已加载" /data/logs/latest.log | tail -4'
echo '--- mixin 应用检查 ---'
docker exec qlm-minecraft sh -c 'grep -iE "qlmzombie.mixins|MixinLivingEntity|mixin.*apply" /data/logs/latest.log | head -8'
echo '--- 容器状态 + 握手 ---'
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm-minecraft
docker exec qlm-minecraft sh -c 'mc-monitor status --host localhost --port 25565 2>&1'
'''))
c.close()
