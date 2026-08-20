# -*- coding: utf-8 -*-
"""最终验证：服务器稳定 + 连接握手 + player channel 已移除"""
import paramiko

key = paramiko.Ed25519Key.from_private_key_file(r'd:\mcmod\SeverAdmin\154.222.28.103_id_ed25519')
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('154.222.28.103', port=4066, username='root', pkey=key, timeout=30)

script = r'''
echo '=== 1. 容器状态 + 运行时长 ==='
docker ps --format '{{.Names}} | {{.Status}}' | grep qlm
echo ''
echo '=== 2. 加载 mod 数量（FMLModList）==='
docker exec qlm-minecraft sh -c 'grep -cE "FMLMod:" /data/logs/latest.log 2>/dev/null'
echo ''
echo '=== 3. player channel 是否注册（应为空）==='
docker exec qlm-minecraft sh -c 'grep -iE "Registering C2S receiver with id playerengine|Registering C2S receiver with id player2npc" /data/logs/latest.log || echo "无 player channel OK"'
echo ''
echo '=== 4. 连接测试（握手）==='
docker exec qlm-minecraft sh -c 'mc-monitor status --host localhost --port 25565 2>&1'
echo ''
echo '=== 5. 最近 30 分钟玩家连接尝试 ==='
docker exec qlm-minecraft sh -c 'grep -iE "logged in|joined the game|Disconnecting|mismatched|rejected" /data/logs/latest.log | tail -8'
echo ''
echo '=== 6. 崩溃报告（应无新）==='
docker exec qlm-minecraft sh -c 'ls -lat /data/crash-reports/ | head -3'
echo ''
echo '=== 7. 服务器运行 jar ==='
docker exec qlm-minecraft sh -c 'ls /data/mods/qlmzombie*.jar'
echo ''
echo '=== 8. mods 目录总数 ==='
docker exec qlm-minecraft sh -c 'ls /data/mods/*.jar 2>/dev/null | wc -l'
'''
stdin, stdout, stderr = c.exec_command(script, timeout=120)
print(stdout.read().decode())
err = stderr.read().decode()
if err:
    print('--- STDERR ---')
    print(err[:800])
c.close()
