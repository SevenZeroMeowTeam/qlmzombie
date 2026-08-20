# -*- coding: utf-8 -*-
"""确认 mods 状态并显式重启 minecraft 容器加载 build63"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

def run(cmd, timeout=300):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    return stdout.read().decode('utf-8', errors='replace'), stderr.read().decode('utf-8', errors='replace')

print('========== 1. 确认 mods 状态 ==========')
out, _ = run(r'''
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar 2>/dev/null'
echo '--- player 残留检查 ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 OK"'
echo '--- 服务器 mc/mods 目录 ---'
ls /www/wwwroot/minecraftsc/mc/mods/qlmzombie*.jar 2>/dev/null
ls /www/wwwroot/minecraftsc/mc/mods/ | grep -iE 'playerengine|player2npc' || echo "mc/mods 无残留 OK"
''', timeout=90)
print(out)

print('========== 2. 重启 minecraft 容器（加载 build63） ==========')
out, _ = run('cd /www/wwwroot/minecraftsc && docker compose restart minecraft 2>&1', timeout=180)
print(out)

print('========== 3. 等待 Done ==========')
out, _ = run(r'''
for i in $(seq 1 50); do
  if docker exec qlm-minecraft sh -c 'grep -q "Done (" /data/logs/latest.log 2>/dev/null'; then
    echo "已启动（第 ${i} 次检查）"
    break
  fi
  sleep 15
done
docker exec qlm-minecraft grep -E 'Done \(' /data/logs/latest.log | tail -1
''', timeout=900)
print(out)

print('========== 4. 验证 ==========')
out, _ = run(r'''
echo '--- 容器状态 ---'
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo '--- 加载的 jar ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- player 残留 ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 OK"'
echo '--- 版本日志 ---'
docker exec qlm-minecraft sh -c 'grep -E "build63|已加载|PlayerEngine|player2npc" /data/logs/latest.log | head -10'
''', timeout=180)
print(out)

c.close()
