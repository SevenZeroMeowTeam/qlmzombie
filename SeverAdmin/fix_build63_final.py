# -*- coding: utf-8 -*-
"""修复：删除 mc/libs 遗留 build62 主 jar，确保 mc/mods 只有 build63，重启容器"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

def run(cmd, timeout=300):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    return stdout.read().decode('utf-8', errors='replace')

print('========== 1. 删除 mc/libs 中的 qlmzombie 主 jar（主 jar 不应在 libs） ==========')
print(run(r'''
rm -f /www/wwwroot/minecraftsc/mc/libs/qlmzombie-*.jar
echo '--- mc/libs 中 qlmzombie 残留 ---'
ls /www/wwwroot/minecraftsc/mc/libs/qlmzombie-*.jar 2>/dev/null || echo "已清理 OK"
'''))
print('========== 2. 清空 mc/mods 中 player 残留 + 确保只有 build63 ==========')
print(run(r'''
rm -f /www/wwwroot/minecraftsc/mc/mods/playerengine*.jar /www/wwwroot/minecraftsc/mc/mods/player2npc*.jar
rm -f /www/wwwroot/minecraftsc/mc/mods/playerengine*.jar.disabled /www/wwwroot/minecraftsc/mc/mods/player2npc*.jar.disabled
# 删除 mc/mods 中所有旧 qlmzombie jar（build62 等），只留 build63
for j in /www/wwwroot/minecraftsc/mc/mods/qlmzombie-*.jar; do
  [ -f "$j" ] || continue
  if [ "$(basename "$j")" != "qlmzombie-3.0.0.beta.build63.jar" ]; then
    rm -f "$j"
    echo "删除旧主 jar: $(basename "$j")"
  fi
done
echo '--- mc/mods qlmzombie jar ---'
ls /www/wwwroot/minecraftsc/mc/mods/qlmzombie-*.jar
echo '--- mc/mods player 残留 ---'
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
echo '--- 容器内 mods 的 qlmzombie ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- 容器内 player 残留 ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 OK"'
echo '--- 版本 + channel 注册 ---'
docker exec qlm-minecraft sh -c 'grep -E "build63|已加载|PlayerEngine|player2npc" /data/logs/latest.log | head -10'
'''))
c.close()
