# -*- coding: utf-8 -*-
"""修复：把 build63 复制到 mc/mods，清理残留，重启验证"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

def run(cmd, timeout=300):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    return stdout.read().decode('utf-8', errors='replace')

print('========== 1. 复制 build63 到 mc/mods ==========')
print(run(r'''
cp -f /www/wwwroot/build/libs/qlmzombie-3.0.0.beta.build63.jar /www/wwwroot/minecraftsc/mc/mods/
ls -la /www/wwwroot/minecraftsc/mc/mods/qlmzombie*.jar
echo '--- player 残留 ---'
ls /www/wwwroot/minecraftsc/mc/mods/ | grep -iE 'playerengine|player2npc' || echo "无残留 OK"
'''))
print('========== 2. 重启 minecraft 容器 ==========')
print(run('cd /www/wwwroot/minecraftsc && docker compose restart minecraft 2>&1'))
print('========== 3. 等待新 Done（清空 latest.log 后重新检查）==========')
print(run(r'''
# 记录当前 latest.log 行数，等待出现新的 Done
sleep 5
docker exec qlm-minecraft sh -c 'tail -3 /data/logs/latest.log'
echo '--- 等待启动 ---'
for i in $(seq 1 50); do
  if docker exec qlm-minecraft sh -c 'grep -q "Done (" /data/logs/latest.log 2>/dev/null'; then
    echo "已启动（第 ${i} 次检查）"
    break
  fi
  sleep 15
done
docker exec qlm-minecraft grep -E 'Done \(' /data/logs/latest.log | tail -1
''', timeout=900))
print('========== 4. 验证 ==========')
print(run(r'''
echo '--- 容器状态 ---'
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo '--- 容器内 mods 的 qlmzombie ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- 容器内 player 残留 ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 OK"'
echo '--- 版本日志（新启动段）---'
docker exec qlm-minecraft sh -c 'grep -E "build63|已加载|PlayerEngine|player2npc|Registering C2S receiver with id player" /data/logs/latest.log | tail -8'
'''))
c.close()
