// 七零喵僵尸末日生存mod - 丰收之月作物催熟
// 丰收月期间定期催熟玩家附近作物（替代 BlockEvents.randomTick，KubeJS 6 已移除该事件）

(() => {
var MoonHelper = Java.loadClass('com.qlm.zombie.moon.MoonHelper')
var ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel')
var BlockPos = Java.loadClass('net.minecraft.core.BlockPos')

LevelEvents.tick('minecraft:overworld', event => {
  var level = event.level
  if (!(level instanceof ServerLevel)) return
  if (!MoonHelper.isHarvestMoon(level)) return
  if (event.server.tickCount % 200 !== 0) return

  event.server.players.forEach(function(player) {
    var pos = player.blockPosition()
    var radius = 8
    for (var x = -radius; x <= radius; x++) {
      for (var y = -2; y <= 2; y++) {
        for (var z = -radius; z <= radius; z++) {
          MoonHelper.forceGrowCrop(level, new BlockPos(pos.x + x, pos.y + y, pos.z + z))
        }
      }
    }
  })
})
})()
