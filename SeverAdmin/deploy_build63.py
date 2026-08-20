# -*- coding: utf-8 -*-
"""部署 build63：上传 jar + libs-list，清理 playerengine/player2npc 残留，deploy.sh docker"""
import paramiko, os

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=60)

local_jar = r'd:\mcmod\build\libs\qlmzombie-3.0.0.beta.build63.jar'
remote_jar = '/www/wwwroot/build/libs/qlmzombie-3.0.0.beta.build63.jar'
local_list = r'd:\mcmod\SeverAdmin\mc\libs-list.txt'
remote_list = '/www/wwwroot/minecraftsc/mc/libs-list.txt'

def run(cmd, timeout=900):
    stdin, stdout, stderr = c.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode('utf-8', errors='replace')
    err = stderr.read().decode('utf-8', errors='replace')
    return out, err

print('========== 1. 上传 build63 jar ==========')
sftp = c.open_sftp()
sftp.put(local_jar, remote_jar, callback=lambda sent, total: None)
print('jar 上传完成:', os.path.getsize(local_jar), 'bytes')
sftp.put(local_list, remote_list)
print('libs-list.txt 上传完成')
sftp.close()

print('========== 2. 清理服务器 playerengine/player2npc 残留 ==========')
out, err = run(r'''
echo '--- 部署前残留检查 ---'
ls -la /www/wwwroot/minecraftsc/mc/mods/ | grep -iE 'playerengine|player2npc' || echo 'mc/mods 无残留'
ls -la /www/wwwroot/minecraftsc/mc/libs/ | grep -iE 'playerengine|player2npc' || echo 'mc/libs 无残留'
echo '--- 清理 mc/mods 与 mc/libs 残留 ---'
rm -f /www/wwwroot/minecraftsc/mc/mods/playerengine*.jar /www/wwwroot/minecraftsc/mc/mods/player2npc*.jar
rm -f /www/wwwroot/minecraftsc/mc/mods/playerengine*.jar.disabled /www/wwwroot/minecraftsc/mc/mods/player2npc*.jar.disabled
rm -f /www/wwwroot/minecraftsc/mc/libs/playerengine*.jar /www/wwwroot/minecraftsc/mc/libs/player2npc*.jar
echo '--- 清理后检查 ---'
ls /www/wwwroot/minecraftsc/mc/mods/ | grep -iE 'playerengine|player2npc' || echo 'mc/mods 已清理'
ls /www/wwwroot/minecraftsc/mc/libs/ | grep -iE 'playerengine|player2npc' || echo 'mc/libs 已清理'
''', timeout=120)
print(out)
if err: print('STDERR:', err[:500])

print('========== 3. 运行远程 deploy.sh docker ==========')
out, err = run('cd /www/wwwroot/minecraftsc && bash deploy.sh docker 2>&1 | tail -25', timeout=1500)
print(out[-2200:])
if err: print('STDERR:', err[:400])

print('========== 4. 等待服务器启动 ==========')
out, err = run(r'''
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

print('========== 5. 验证 ==========')
out, err = run(r'''
echo '--- 容器状态 ---'
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo '--- 服务器 mods 中 player 残留 ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 ✓"'
echo '--- 加载的 jar ---'
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo '--- 日志中的版本 + playerengine 加载 ---'
docker exec qlm-minecraft sh -c 'grep -E "build63|PlayerEngine|player2npc|Registering C2S receiver with id playerengine" /data/logs/latest.log | head -10'
''', timeout=180)
print(out)
if err: print('STDERR:', err[:500])

c.close()
