# Thirst-Mod (Thirst was Taken) — MIT License 署名

本项目的口渴系统模块（`com.qlm.zombie.thirst`）整合自开源模组：

**Thirst-Mod / Thirst was Taken**
- 作者: ghen (ghen-git)，贡献者: mlus-asuka、eyeofflame、Enderteck
- 仓库: https://github.com/ghen-git/Thirst-Mod
- 分支/版本: `1.20.1`（v1.20.1-1.3.15）
- 许可: MIT License
- 原包名: `dev.ghen.thirst` → 整合后包名: `com.qlm.zombie.thirst`

整合内容（保留纹理与物品）：
- 物品：`clay_bowl`、`terracotta_bowl`、`terracotta_water_bowl`（含物品纹理/模型/语言）
- 机制：水质系统（脏/微脏/可接受/净化）、口渴值/解渴值能力（IThirst capability）、脱水伤害、口渴 HUD、口渴命令
- 数据：净化配方（熔炉/营火）、战利品（宝箱水容器）、伤害类型标签
- 纹理：`assets/qlmzombie/textures/item/*`、`assets/qlmzombie/textures/gui/thirst_icons.png`

按开源准则移除（未纳入本项目/未安装模组的兼容内容）：
- Create 兼容（沙滤器方块、ponder 场景）
- Botania、ToughAsNails、FarmersRespite、BrewinAndChewin、Jade、AppleSkin 兼容 mixin

---

## MIT License

Copyright (c) ghen (ghen-git) — Thirst-Mod (Thirst was Taken)
Copyright (c) SevenZeroMeow Team — 整合改编（包名/命名空间调整）

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
