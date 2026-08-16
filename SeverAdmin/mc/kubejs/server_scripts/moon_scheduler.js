// 七零喵僵尸末日生存mod - 月相调度器
// 安全日期间(1-25天)不触发血月；安全日过后每 14 天一次血月
// 非血月夜 7% 概率幸运月；7% 概率丰收月
// 通过调用本 mod 的 MoonHelper 间接操作 Enhanced Celestials

(() => {
var MoonHelper = Java.loadClass('com.qlm.zombie.moon.MoonHelper')
var ServerLevel = Java.loadClass('net.minecraft.server.level.ServerLevel')
var QLMConfig = Java.loadClass('com.qlm.zombie.config.QLMConfig')
var BLOOD_MOON_INTERVAL = 14
var SAFE_DAYS_END = QLMConfig.PEACEFUL_DAYS.get()
var LUCKY_MOON_CHANCE = 0.07
var HARVEST_MOON_CHANCE = 0.07
var DAY_LENGTH = 24000
var DUSK_START = 13000
var DUSK_END = 13100
var THROTTLE_TICKS = 100

var tickCounter = 0

LevelEvents.tick('minecraft:overworld', event => {
  var level = event.level
  if (!(level instanceof ServerLevel)) return

  if (++tickCounter < THROTTLE_TICKS) return
  tickCounter = 0

  var dayTime = MoonHelper.getDayTime(level)
  var timeOfDay = dayTime % DAY_LENGTH
  if (timeOfDay < DUSK_START || timeOfDay > DUSK_END) return

  var day = MoonHelper.getDay(level)
  var pd = event.server.persistentData
  var lastScheduledDay = pd.getLong('qlmzombie.lastScheduledDay')

  if (lastScheduledDay === day) return

  var currentMoon = MoonHelper.getCurrentMoonId(level)
  if (currentMoon !== 'enhancedcelestials:default' && currentMoon !== 'none') {
    pd.putLong('qlmzombie.lastScheduledDay', day)
    return
  }

  var scheduled = false
  if (day > SAFE_DAYS_END && day % BLOOD_MOON_INTERVAL === 0) {
    scheduled = MoonHelper.forceBloodMoon(level)
    console.log('[QLM] Day ' + day + ' -> Blood Moon (forced)')
  } else {
    var r = Math.random()
    if (r < LUCKY_MOON_CHANCE) {
      scheduled = MoonHelper.forceLuckyMoon(level)
      console.log('[QLM] Day ' + day + ' -> Lucky Moon (random)')
    } else if (r < LUCKY_MOON_CHANCE + HARVEST_MOON_CHANCE) {
      scheduled = MoonHelper.forceHarvestMoon(level)
      console.log('[QLM] Day ' + day + ' -> Harvest Moon (random)')
    }
  }

  if (scheduled || currentMoon !== 'enhancedcelestials:default') {
    pd.putLong('qlmzombie.lastScheduledDay', day)
  }
})
})()
