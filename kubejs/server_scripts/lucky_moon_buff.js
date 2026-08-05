// 七零喵僵尸末日生存mod - 幸运之月效果
// 幸运之月期间给所有在线玩家持续刷新 Luck buff 直到天亮

const MoonHelper = Java.loadClass('com.qlm.zombie.moon.MoonHelper')
const ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel')
const LUCK_DURATION = 600   // 30 秒，每 30 秒刷新一次保证覆盖整夜
const LUCK_AMPLIFIER = 1    // Luck II

PlayerEvents.tick(event => {
  // 每 60 tick (3 秒) 才检查，避免每 tick 都遍历玩家
  if (event.server.tickCount % 60 !== 0) return
  if (event.player.level === null) return
  const level = event.player.level
  if (!(level instanceof ServerLevel)) return

  if (!MoonHelper.isLuckyMoon(level)) return

  // 给当前玩家刷新 Luck buff
  event.player.potionEffects.add('minecraft:luck', LUCK_DURATION, LUCK_AMPLIFIER, true, true)
})

// 服务端 tick 兜底：确保所有在线玩家都拿到 buff
LevelEvents.tick('minecraft:overworld', event => {
  if (event.server.tickCount % 100 !== 0) return
  const level = event.level
  if (!(level instanceof ServerLevel)) return
  if (!MoonHelper.isLuckyMoon(level)) return

  event.server.players.forEach(player => {
    player.potionEffects.add('minecraft:luck', LUCK_DURATION, LUCK_AMPLIFIER, true, true)
  })
})
