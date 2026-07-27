// 七零喵僵尸末日生存mod - 月相调度器
// 安全日期间(1-25天)不触发血月；安全日过后每 14 天一次血月
// 非血月夜 7% 概率幸运月；7% 概率丰收月
// 通过调用本 mod 的 MoonHelper 间接操作 Enhanced Celestials

const MoonHelper = Java.loadClass('com.qlm.zombie.moon.MoonHelper')
const ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel')
const QLMConfig = Java.loadClass('com.qlm.zombie.config.QLMConfig')
const BLOOD_MOON_INTERVAL = 14
const SAFE_DAYS_END = QLMConfig.PEACEFUL_DAYS.get()
const LUCKY_MOON_CHANCE = 0.07
const HARVEST_MOON_CHANCE = 0.07
const DAY_LENGTH = 24000
const DUSK_START = 13000
const DUSK_END = 13100
const THROTTLE_TICKS = 100

let tickCounter = 0

LevelEvents.tick('minecraft:overworld', event => {
  const level = event.level
  if (!(level instanceof ServerLevel)) return

  if (++tickCounter < THROTTLE_TICKS) return
  tickCounter = 0

  // 仅在黄昏（约 13000 tick）触发调度
  const dayTime = MoonHelper.getDayTime(level)
  const timeOfDay = dayTime % DAY_LENGTH
  if (timeOfDay < DUSK_START || timeOfDay > DUSK_END) return

  const day = MoonHelper.getDay(level)
  const pd = event.server.persistentData
  const lastScheduledDay = pd.getLong('qlmzombie.lastScheduledDay')

  // 当天已调度过则跳过
  if (lastScheduledDay === day) return

  const currentMoon = MoonHelper.getCurrentMoonId(level)
  // 如果 EC 已经分配了一个非默认月相，不要覆盖
  if (currentMoon !== 'enhancedcelestials:default' && currentMoon !== 'none') {
    pd.putLong('qlmzombie.lastScheduledDay', day)
    return
  }

  let scheduled = false
  // 安全日期间(1-14天)不触发血月，安全日过后(第15天起)才触发血月
  if (day > SAFE_DAYS_END && day % BLOOD_MOON_INTERVAL === 0) {
    scheduled = MoonHelper.forceBloodMoon(level)
    console.log(`[QLM] Day ${day} -> Blood Moon (forced)`)
  } else {
    const r = Math.random()
    if (r < LUCKY_MOON_CHANCE) {
      scheduled = MoonHelper.forceLuckyMoon(level)
      console.log(`[QLM] Day ${day} -> Lucky Moon (random)`)
    } else if (r < LUCKY_MOON_CHANCE + HARVEST_MOON_CHANCE) {
      scheduled = MoonHelper.forceHarvestMoon(level)
      console.log(`[QLM] Day ${day} -> Harvest Moon (random)`)
    }
  }

  if (scheduled || currentMoon !== 'enhancedcelestials:default') {
    pd.putLong('qlmzombie.lastScheduledDay', day)
  }
})