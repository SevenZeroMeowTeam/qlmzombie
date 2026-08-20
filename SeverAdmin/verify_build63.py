# -*- coding: utf-8 -*-
"""验证 build63 部署：无 playerengine、版本正确、服务器稳定"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

script = r'''
echo '=== 1. 容器状态 ==='
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo ''
echo '=== 2. 加载的 qlmzombie jar ==='
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo ''
echo '=== 3. mods 中 player 残留检查 ==='
docker exec qlm-minecraft sh -c 'ls /data/mods/ | grep -iE "playerengine|player2npc" || echo "无残留 OK"'
echo ''
echo '=== 4. 服务器 jar 内嵌 libs 是否含 player ==='
docker exec qlm-minecraft sh -c 'unzip -l /data/mods/qlmzombie-*.jar 2>/dev/null | grep -iE "playerengine|player2npc" || echo "内嵌无 player OK"'
echo ''
echo '=== 5. 日志版本 + 加载状态 ==='
docker exec qlm-minecraft sh -c 'grep -E "build63|已加载|PlayerEngine|player2npc|Registering C2S receiver with id player" /data/logs/latest.log | head -12'
echo ''
echo '=== 6. 服务器 Done 时间 ==='
docker exec qlm-minecraft sh -c 'grep "Done (" /data/logs/latest.log | tail -1'
echo ''
echo '=== 7. 在线玩家 ==='
docker exec qlm-minecraft sh -c 'grep -E "joined the game" /data/logs/latest.log | tail -3'
echo ''
echo '=== 8. 最近崩溃（应无新崩溃）===' 
docker exec qlm-minecraft sh -c 'ls -lat /data/crash-reports/ | head -3'
echo ''
echo '=== 9. mods 总数量 ==='
docker exec qlm-minecraft sh -c 'ls /data/mods/*.jar 2>/dev/null | wc -l'
'''
stdin, stdout, stderr = c.exec_command(script, timeout=180)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print('--- STDERR ---')
    print(err[:1000])
c.close()
