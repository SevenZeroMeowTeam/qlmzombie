// 七零喵僵尸末日生存mod - 丰收之月作物催熟
// 丰收月期间作物每次随机刻额外生长一次（等效随机刻强度翻倍）

const MoonHelper = Java.loadClass('com.qlm.zombie.moon.MoonHelper')
const ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel')

BlockEvents.randomTick(event => {
  const level = event.level
  if (!(level instanceof ServerLevel)) return
  if (!MoonHelper.isHarvestMoon(level)) return

  // MoonHelper.forceGrowCrop 内部会判断该方块是否为 CropBlock，
  // 是则调用 CropBlock.growCrops 强制生长一次
  MoonHelper.forceGrowCrop(level, event.pos)
})
