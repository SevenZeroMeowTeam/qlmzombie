// 七零喵僵尸末日生存mod — 逻辑单元测试
// 模拟 DayPhase、血月调度、僵尸进化的核心逻辑（与 Java 版保持一致）

const assert = (cond, msg) => {
  if (!cond) {
    console.error('  FAIL: ' + msg)
    process.exitCode = 1
  } else {
    console.log('  PASS: ' + msg)
  }
}

// ============ DayPhase 逻辑 ============
console.log('\n=== DayPhase 阶段划分 ===')

function dayPhaseForDay(day) {
  if (day >= 1 && day <= 10) return { name: 'SAFE', difficulty: 'PEACEFUL', locked: false }
  if (day >= 11 && day <= 31) return { name: 'EASY', difficulty: 'EASY', locked: false }
  if (day >= 32 && day <= 52) return { name: 'NORMAL', difficulty: 'NORMAL', locked: false }
  if (day >= 53) return { name: 'HARD', difficulty: 'HARD', locked: true }
  return { name: 'HARD', difficulty: 'HARD', locked: true }
}

// 边界值测试
const dayCases = [
  [0, 'HARD'],     // 第 0 天（未开始，回退到 HARD）
  [1, 'SAFE'],
  [5, 'SAFE'],
  [10, 'SAFE'],
  [11, 'EASY'],
  [20, 'EASY'],
  [31, 'EASY'],
  [32, 'NORMAL'],
  [42, 'NORMAL'],
  [52, 'NORMAL'],
  [53, 'HARD'],
  [100, 'HARD'],
  [9999, 'HARD'],
]
for (const [day, expected] of dayCases) {
  const phase = dayPhaseForDay(day)
  assert(phase.name === expected, `第 ${day} 天应为 ${expected}，实际 ${phase.name}`)
}

// 锁定测试
assert(dayPhaseForDay(52).locked === false, '第 52 天不应锁定')
assert(dayPhaseForDay(53).locked === true, '第 53 天应锁定')
assert(dayPhaseForDay(100).locked === true, '第 100 天应锁定')

// ============ 血月调度 ============
console.log('\n=== 血月调度 (每 14 天) ===')

const BLOOD_MOON_INTERVAL = 14

function shouldBeBloodMoon(day) {
  return day > 0 && day % BLOOD_MOON_INTERVAL === 0
}

const bloodMoonCases = [
  [1, false],
  [13, false],
  [14, true],
  [15, false],
  [27, false],
  [28, true],
  [42, true],
  [56, true],
  [100, false],
  [112, true],
  [0, false],
]
for (const [day, expected] of bloodMoonCases) {
  const result = shouldBeBloodMoon(day)
  assert(result === expected, `第 ${day} 天血月应为 ${expected}，实际 ${result}`)
}

// 统计 365 天内血月次数
let bloodMoonCount = 0
for (let d = 1; d <= 365; d++) if (shouldBeBloodMoon(d)) bloodMoonCount++
assert(bloodMoonCount === Math.floor(365 / 14), `365 天内血月次数应为 26，实际 ${bloodMoonCount}`)
console.log(`  INFO: 365 天内血月 ${bloodMoonCount} 次 (每14天一次)`)

// ============ 僵尸进化概率 ============
console.log('\n=== 僵尸进化概率分布 ===')

const EVOLVE_CHANCES = {
  SAFE: 0.0,
  EASY: 0.10,
  NORMAL: 0.25,
  HARD: 0.40,
}

// 用固定种子的简易 RNG 做蒙特卡洛验证
function mulberry32(a) {
  return function() {
    a |= 0; a = a + 0x6D2B79F5 | 0
    let t = Math.imul(a ^ a >>> 15, 1 | a)
    t = t + Math.imul(t ^ t >>> 7, 61 | t) ^ t
    return ((t ^ t >>> 14) >>> 0) / 4294967296
  }
}

const TRIALS = 100000
let stats = {}
for (const phase of ['SAFE', 'EASY', 'NORMAL', 'HARD']) {
  const rng = mulberry32(phase.charCodeAt(0) * 1000)
  let evolved = 0
  for (let i = 0; i < TRIALS; i++) {
    if (rng() < EVOLVE_CHANCES[phase]) evolved++
  }
  const rate = evolved / TRIALS
  const expected = EVOLVE_CHANCES[phase]
  const diff = Math.abs(rate - expected)
  const ok = diff < 0.02
  assert(ok, `${phase} 阶段进化率实测 ${(rate * 100).toFixed(2)}%，期望 ${(expected * 100).toFixed(0)}%，偏差 ${(diff * 100).toFixed(2)}%`)
}

// ============ Lucky Moon 随机分布测试
console.log('\n=== 幸运月/丰收月概率分布 (蒙特卡洛===')

const LUCKY_CHANCE = 0.07
const HARVEST_CHANCE = 0.07
const BLOOD_INTERVAL = 14

function simulateOneYear(days = 365) {
  const rng = mulberry32(days * 7 + 42)
  let blood = 0, lucky = 0, harvest = 0, none = 0
  for (let d = 1; d <= days; d++) {
    if (d % BLOOD_INTERVAL === 0) { blood++ }
    else {
      const r = rng()
      if (r < LUCKY_CHANCE) lucky++
      else if (r < LUCKY_CHANCE + HARVEST_CHANCE) harvest++
      else none++
    }
  }
  return { blood, lucky, harvest, none, total: days }
}

const sim = simulateOneYear(36500)  // 10 年取平均更准
const nonBloodDays = sim.total - sim.blood
const luckyRate = sim.lucky / nonBloodDays
const harvestRate = sim.harvest / nonBloodDays
assert(Math.abs(luckyRate - 0.07) < 0.01, `幸运月率 ${(luckyRate * 100).toFixed(2)}% ≈ 7%`)
assert(Math.abs(harvestRate - 0.07) < 0.01, `丰收月率 ${(harvestRate * 100).toFixed(2)}% ≈ 7%`)
console.log(`  INFO: 10年模拟 - 血月${sim.blood} 幸运月${sim.lucky} 丰收月${sim.harvest} 普通夜${sim.none}`)

// ============ 难度锁定回退测试
console.log('\n=== 难度锁定回退测试 ===')

function difficultyLockLogic(currentDifficulty, isLocked, newDifficulty) {
  // 模拟 DayPhaseManager 逻辑：若锁定且目标难度不是 HARD 就回滚
  if (isLocked && newDifficulty !== 'HARD') {
    return { allowed: false, effective: 'HARD' }
  }
  return { allowed: true, effective: newDifficulty }
}

const lockCases = [
  ['EASY', true, 'PEACEFUL', false, 'HARD'],
  ['HARD', true, 'NORMAL', false, 'HARD'],
  ['HARD', true, 'HARD', true, 'HARD'],
  ['NORMAL', false, 'EASY', true, 'EASY'],
  ['EASY', false, 'NORMAL', true, 'NORMAL'],
]
for (const [cur, locked, newDiff, expAllow, expEff] of lockCases) {
  const r = difficultyLockLogic(cur, locked, newDiff)
  assert(r.allowed === expAllow && r.effective === expEff,
    `当前${cur} 锁定=${locked} 切${newDiff} → 允许=${expAllow} 生效=${expEff} (实际: 允许=${r.allowed} 生效=${r.effective})`)
}

console.log('\n=== 全部测试完成 ===')
if (process.exitCode === 1) {
  console.log('有测试失败!')
} else {
  console.log('所有测试通过!')
}
