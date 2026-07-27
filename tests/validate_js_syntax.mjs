import { readFileSync, readdirSync, statSync } from 'node:fs'
import { parse } from 'node:path'

const base = 'c:/Users/Administrator/Desktop/mod开发/七零喵僵尸末日生存mod/kubejs/server_scripts'

function listFiles(dir) {
  const out = []
  for (const f of readdirSync(dir)) {
    const full = dir + '/' + f
    const s = statSync(full)
    if (s.isDirectory()) out.push(...listFiles(full))
    else if (f.endsWith('.js')) out.push(full)
  }
  return out
}

const files = listFiles(base)
let pass = 0, fail = 0

for (const f of files) {
  const src = readFileSync(f, 'utf8')
  // 用 Function 构造器做语法检查（不执行）
  try {
    // 把顶层 const/let/var 包在函数里以支持函数级声明检查
    new Function(src)
    console.log('PASS JS   ' + f.replace(base + '/', ''))
    pass++
  } catch (e) {
    console.log('FAIL JS   ' + f.replace(base + '/', '') + ': ' + e.message)
    fail++
  }
}

console.log(`\nJS syntax: ${pass} passed, ${fail} failed`)
process.exit(fail > 0 ? 1 : 0)
