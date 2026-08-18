# 远程服务器部署说明（2026-08-18 build47 更新）

> 目标：`154.222.28.103`（`mc.sh197.dpdns.org`）
> 远程路径：mcmod `/www/wwwroot`，SeverAdmin `/www/wwwroot/minecraftsc`

## 🆕 2026-08-18 build47：修复 Missing Texture 根因（atlas 未扫描 cd/ 子目录）

- **根因诊断**（build46 后仍 Missing Texture）：Minecraft 1.20.1 默认 item atlas 只扫描 `textures/item/*.png`（0 层深度），`textures/item/cd/*.png` 下 48 张 CD 物品纹理**从未被加载到 atlas**；atlases/ 目录完全不存在 → 扩展配置缺失。此前的尺寸/格式/缺失文件修复都正确但未生效的真正原因。
- **修复动作**：
  1. 新增 `assets/qlmzombie/atlases/item.json`：sources → directory `textures/item` + `textures/item/cd`（扩展默认 item atlas 扫描 cd/ 子目录 48 张物品纹理）
  2. 新增 `assets/qlmzombie/atlases/block.json`：sources → directory `textures/block`（确保方块纹理正确加载）
  3. 完整审计 71 个注册 ID（CDItems 46 + QLMItems 18 + Thirst 3 + BlockItems 4）：全部验证 model.json + texture.png(16×16 RGBA32) + blockstate.json 存在，**0 FAIL, 71 OK**
- 版本号 `3.0.0.beta.build47`，登录公告 v4.4
- **必须上传**：`build/libs/qlmzombie-3.0.0.beta.build47.jar`（→ `/www/wwwroot/build/libs/`）
- **部署**：上传 jar + SeverAdmin 脚本 + ftbquests 配置 → 远程 `./deploy.sh docker` → **⚠️ 必须手动 `docker restart qlm-minecraft`**（compose up 镜像未变不会重启容器）
- **部署后验证**：日志 `v3.0.0.beta.build47 已加载`、无当日新 crash、FTB 4 chapters 30 quests、KubeJS 7/7 0 errors、`/api/status` online

## 🆕 2026-08-18 build46：彻底修复全部物品 Missing Texture

- **68 张 PNG 中 62 张尺寸错误**统一重采样为 16×16 ARGB32（48 张 CD 物品 + 10 张 QLM 物品 + 2 张 128×128）
- **supply_crate 崩溃修复**：512×512 RGB24（无 Alpha）→ 16×16 RGBA32；ammo/medical crate 同步 16×16
- **程序化生成 6 张缺失贴图**（3 僵尸蛋 / fake_player 蛋 / plank_axe / plank_collector）
- **CDCreativeTabs 修复**：CD_BLOCKS 补上 SUPPLY_CRATE_ITEM（方块栏恢复）
- 版本号 `3.0.0.beta.build46`，登录公告 v4.3
- **必须上传**：`build/libs/qlmzombie-3.0.0.beta.build46.jar`（→ `/www/wwwroot/build/libs/`）
- **部署**：上传 jar + SeverAdmin 脚本 + ftbquests 配置 → 远程 `./deploy.sh docker` → **⚠️ 必须手动 `docker restart qlm-minecraft`**（compose up 镜像未变不会重启容器）
- **部署后验证**：日志 `v3.0.0.beta.build46 已加载`、无当日新 crash、FTB 4 chapters 30 quests、KubeJS 7/7 0 errors

## 🆕 2026-08-18 build45：医疗物品使用动画 + 贴图增强

- **9 个医疗物品新增 DRINK 使用动画**（MedicalUseItem 基类：绷带/急救包/肾上腺素/止痛药/止血带/夹板/生理盐水/手术剪/解毒剂）
- **20 个物品贴图程序化增强**（32×32 渐变+高光+阴影，AI 生图风格）
- **建筑箱子保证至少 1 个其他模组物品**（StructureGenSupport，10954 物品池）
- 版本号 `3.0.0.beta.build45`，登录公告新增「医疗用品使用动画」
- **必须上传**：`build/libs/qlmzombie-3.0.0.beta.build45.jar`（→ `/www/wwwroot/build/libs/`）
- **部署**：`python build/ftbq_check/deploy_server.py`（MD5 校验 → 清理旧 jar → 复制 → compose up → docker restart）
- **部署后验证**：日志 `v3.0.0.beta.build45 已加载`、无崩溃、FTB Quests 4 chapters 30 quests

## 🆕 2026-08-18 build44：物品材质补全 + 建筑箱子其他模组物品

- **自动生成 55 个缺失物品贴图**（`build/gen_item_textures.py`，PIL），覆盖全部主物品 + CD 物品，修复紫黑方块
- **46 个 CD 物品补全中英文 lang**，清理 4 个过期 lang 键
- **建筑箱子保证至少 1 个其他模组物品**（StructureGenSupport.fillChest 增强）
- 版本号 `3.0.0.beta.build44`，登录公告新增「物品材质已补全」
- **必须上传**：`build/libs/qlmzombie-3.0.0.beta.build44.jar`（→ `/www/wwwroot/build/libs/`）
- **部署**：`python build/ftbq_check/deploy_server.py`（MD5 校验 → 清理旧 jar → 复制 → compose up → docker restart）
- **部署后验证**：日志 `v3.0.0.beta.build44 已加载`、无崩溃、FTB Quests 4 chapters 30 quests

## 🆕 2026-08-17 build43：僵尸攻击力随天数增强 + FTB 任务医疗奖励 + Kotlin 事件修复

- **僵尸攻击力随天数增强**：第 25 天后每过一天攻击力 +1.5%（无上限）——`ZombieEvolutionHandler.kt`
- **FTB 任务医疗奖励**：12 个任务追加 26 个医疗奖励（craftingdead / infectious / zombiekit）
- **Kotlin @JvmStatic 修复**：11 个 Kotlin object 事件订阅器补上 @JvmStatic（此前事件永不触发）
- **ZombieAttributeHandler 崩溃修复**：移除原版僵尸重复属性注册（Duplicate DefaultAttributes），+2 护甲改为生成时修饰符
- **MoonHelper 修复**：Enhanced Celestials `Holder.unwrapKey()` 编译期调用
- **必须上传**：`build/libs/qlmzombie-3.0.0.beta.build43.jar`（→ `/www/wwwroot/build/libs/`）
- **部署**：`python build/ftbq_check/deploy_server.py`（MD5 校验 → 复制到 mods → docker compose up -d）
- **部署后验证**：日志无 `Duplicate DefaultAttributes`、无 `getCurrentMoonId failed`；`建筑生成` 日志出现；FTB Quests 加载 4 chapters 30 quests

## 🆕 2026-08-17 修复：游戏内无任务（FTB Quests 只读 .snbt）

- **根因**：FTB Quests 2001.4.22（1.20.1）从 `config/ftbquests/quests` 只读取 **`*.snbt`** 文件
  （`data.snbt` + `chapters/*.snbt`），**不读 .json5、不读 lang/ 目录**。之前部署的是 `.json5`
  且标题只放 lang/ → 日志 `Loaded 1 chapter groups, 0 chapters, 0 quests` → 游戏内无任务。
- **修复**：由 `build/convert_quests_snbt.py` 生成 `.snbt` 任务文件（标题/描述内联在每个 quest），
  生成到 `SeverAdmin/mc/config/ftbquests/quests/`；`#` 标签物品（如 `#minecraft:logs`）映射为
  具体物品（2001.4.22 ItemTask 不支持标签）。entrypoint/deploy.sh 已改为检查 `*.snbt`。
- **部署时必须上传**：`SeverAdmin/mc/config/ftbquests/quests/`（→ `/www/wwwroot/minecraftsc/mc/config/ftbquests/quests/`）
- **改格式/改内容后必须清容器旧数据再重启**（entrypoint 仅"缺失时复制"）：
  `docker exec qlm-minecraft rm -rf /data/config/ftbquests/quests && docker restart qlm-minecraft`
- **部署后验证**：日志出现 `Loaded 1 chapter groups, 4 chapters, 30 quests`（此前为 0 chapters）

## 🆕 2026-08-17 build42：服务器启用 Yes Steve Model（修复物品错位）+ 修复 18 个编译警告

- **Yes Steve Model（是，史蒂夫模型）2.6.5 在服务端启用**（`side=BOTH`，修复物品错位）：
  - `ModDependencyHandler.SERVER_DISABLED_PREFIXES` 移除 `ysm`
  - `deploy.sh` / `entrypoint-wrapper.sh`（SeverAdmin 与 deploy-upload 两套）的 `CLIENT_ONLY_MODS` 与 python `skip` 均移除 `ysm`
  - 已核验服务端安全：公共 mixin（AbstractArrow/Projectile/ServerPlayer）与 MixinTweaker 无客户端类引用
- **版本**：`3.0.0.beta.build42`（gradle.properties + QLMZombieMod.kt）
- **18 个 javac 弃用警告已修复**（ResourceLocation.parse/fromNamespaceAndPath、ItemStack.getEnchantmentLevel、getFoodProperties(ItemStack,null)、@SuppressWarnings 等）
- **本次必须上传**：`build/libs/qlmzombie-3.0.0.beta.build42.jar`（→ `/www/wwwroot/build/libs/`）、`deploy.sh`、`mc/entrypoint-wrapper.sh`（→ `/www/wwwroot/minecraftsc/`）
- **部署后验证**：mods 含 `[是，史蒂夫模型] ysm-2.6.5-forge+mc1.20.1-release.jar`（无"服务端跳过纯客户端模组"日志）；加载列表含 `yes_steve_model`；玩家手持物品位置正常

## 🆕 2026-08-16 23:35 修复：ToughAsNails 已从源头移除（自动释放清单同步）

已从 `src/main/libs` **彻底移除 ToughAsNails**（不再打包/内嵌/释放），并按当前 `src/main/libs`
（119 个依赖）同步了全部"加载时自动释放"链路：

1. `SeverAdmin/mc/libs-list.txt`、`deploy-upload/libs-list.txt`、`scripts/libs-list.txt`
   —— 移除 ToughAsNails 行，均与 `src/main/libs` 完全一致（119 条，BOM/编码保留：前两者带 BOM，scripts 不带）
2. `SeverAdmin/mc/libs/` —— 删除残留 ToughAsNails jar，补齐缺失的 `[口渴] ThirstWasTaken`（现与 src 一致 119 个）
3. `build/libs/qlmzombie-3.0.0.beta.build40.jar`（**23:3x 最新构建**）—— 内嵌 `libs/` 与
   `libs/manifest.txt` 已重新生成：**无 ToughAsNails、无 qlmzombie**（118 条 = 119 − kotlinforforge，
   符合 JarInJar 设计）。ModDependencyHandler 游戏加载时自动释放将不再释放 ToughAsNails
4. 清理 `src/main/libs` 中误放的 `qlmzombie-3.0.0.beta.build40.jar`（435MB 自身构建产物）
5. `scripts/check-libs.ps1` 验证通过（119/119）

> 结论：ToughAsNails 不再存在 → 登录配方 NPE 问题**彻底消除**（无需再依赖预禁用脚本兜底）。
> 若服务器 `/data` 卷中仍有旧 `[意志坚定] ToughAsNails*.jar(.disabled)` 残留，deploy.sh/
> entrypoint/ModDependencyHandler 的并存清理逻辑仍会兜底删除/禁用，但不影响新部署。

## ⚠️ 本次必须上传的文件

| 本地文件 | 上传到远程 |
| --- | --- |
| `build/libs/qlmzombie-3.0.0.beta.build40.jar`（**23:3x 构建，无 ToughAsNails**） | `/www/wwwroot/build/libs/` |
| `deploy.sh` | `/www/wwwroot/minecraftsc/deploy.sh` |
| `mc/entrypoint-wrapper.sh` | `/www/wwwroot/minecraftsc/mc/entrypoint-wrapper.sh` |
| `mc/libs-list.txt`（**已去 ToughAsNails**） | `/www/wwwroot/minecraftsc/mc/libs-list.txt` |
| `mc/kubejs/server_scripts/airdrop_scheduler.js` | `/www/wwwroot/minecraftsc/mc/kubejs/server_scripts/airdrop_scheduler.js` |
| `mc/agent/patch_cd_jar.py` | `/www/wwwroot/minecraftsc/mc/agent/patch_cd_jar.py` |

> 部署：`./deploy.sh docker`（或上传后 `docker compose up -d && docker restart qlm-minecraft`）。
> 若服务器 `SeverAdmin/mc/libs` 与 `mc/mods` 仍有旧 ToughAsNails jar，请一并删除。

## 部署后验证
1. mods 列表**不含 toughasnails**；玩家正常登录**不被踢**
2. 日志 ModDependencyHandler 显示内嵌 JAR 118 个（白名单）
3. 无 `airdrop_scheduler.js redeclaration of var x`
4. 服务器运行超过 10 分钟不崩溃


### 1. 上传【新】模组 jar + 脚本（上次部署只传了 jar 和内存，脚本没传！）
上次日志（19:45）确认：
- ✅ 内存已提升到 4G（有效）
- ✅ KubeJS 修复已生效（无 `player.stage` 报错）
- ❌ **ToughAsNails 预禁用未生效**（日志无「预禁用冲突模组」→ 上次没上传新 deploy.sh/entrypoint）
- ❌ **仍 60s 崩溃**（根因：主世界堆了 **3869 只僵尸**，见崩溃报告 `zombie:3869`）
- ❌ 玩家仍被踢（EncoderException，ToughAsNails 兼容配方未跳过）

**必须上传全部 6 个文件**：

| 本地文件 | 上传到远程 |
| --- | --- |
| `build/libs/qlmzombie-3.0.0.beta.build40.jar`（**最新 19:5x 构建**，含僵尸人口控制） | `/www/wwwroot/build/libs/` |
| `src/main/libs/[口渴] ThirstWasTaken-1.20.1-1.4.0.jar` | `/www/wwwroot/src/main/libs/` |
| `deploy.sh` | `/www/wwwroot/minecraftsc/deploy.sh` |
| `mc/entrypoint-wrapper.sh` | `/www/wwwroot/minecraftsc/mc/entrypoint-wrapper.sh` |
| `mc/libs-list.txt` | `/www/wwwroot/minecraftsc/mc/libs-list.txt` |
| `mc/kubejs/server_scripts/qlmzombie_scripts.js` | `/www/wwwroot/minecraftsc/mc/kubejs/server_scripts/qlmzombie_scripts.js` |


> 远程若存在旧的 `[口渴] ThirstWasTaken-1.20.1-1.4.0.jar.disabled`，删除它。
> 远程 mods 目录若有 active 的 ToughAsNails jar，部署脚本会自动预禁用（无需手动处理）。

### 2. 提升服务器内存（修复 watchdog 崩溃——根因）
上次日志确认 `Setting initial memory to 2G and max to 2G`，130+ 模组
在 2G 内存下 GC 卡死 → `ServerHangWatchdog: single server tick took 60.00 seconds`
反复崩溃（分别在 deer / SearchMountGoal / onMobTick 卡住）。

编辑远程 `/www/wwwroot/minecraftsc/.env`：
```ini
MC_MAX_MEMORY=4G        # 服务器内存充足可设 6G
```

### 3. 重新部署
```bash
cd /www/wwwroot/minecraftsc
./deploy.sh docker
```

## 本次修复内容（19:55 最新版，新增僵尸人口控制）
- 🆕🆕 **僵尸人口控制**（`ZombiePopulationControl`，新代码）：**watchdog 60s 崩溃的真正根因**
  是主世界堆了 3869 只僵尸（崩溃报告 `entities:4128 [zombie:3869]`）——每 tick 给 4000+
  实体跑 AI/寻路/移动必然卡死，4G 内存也救不了（崩溃位置每次不同：deer/SearchMountGoal/
  寻路/Sona 声呐吸引，全是实体 tick）。新机制：每 5 秒检查主世界僵尸数，超过 **500 只**时
  按"离最近玩家由远到近"移除超限部分（保留玩家 48 格内的战斗对象）。日志会输出
  `[QLM Zombie] 僵尸人口控制: 主世界 X 只僵尸超限，已移除 Y 只`。
- 🆕 **ToughAsNails 预禁用**（`deploy.sh` + `entrypoint`，⚠️ **上次没上传，本次必须传**）：
  服务器加载前将 ToughAsNails 重命名为 `.disabled`。修复两个 NPE：
  - Zeta 配方扫描：`Failed to scan recipe thirst:compat/toughasnails/...`（null ItemStack）
  - **玩家登录被踢**：`EncoderException: Cannot invoke ItemStack.isEmpty() because ... null`
    （登录时配方同步编码了含 null 物品的 ToughAsNails 兼容配方）
  - 原理：ToughAsNails 不加载 → `forge:mod_loaded` 条件失败 → 兼容配方跳过 → 无 NPE
- 🆕 **KubeJS 登录脚本**（已生效）：`player.stage` → `player.persistentData`
- `AIImprovementsFeature$ThrottledGoal.canUse()` 计数器 bug（已生效）：非运行 goal 的
  `canUse()` 原来每 tick 都真实执行 → 已修复按 period 节流
- `MobBehaviorHandler.onMobTick` 已节流（每 10 tick 一次）
- `building_tacz_spartan.json` 已删除 `//` 注释（已生效）
- ThirstWasTaken 白名单（已生效，服务器正常加载 mod "thirst"）

## 部署后验证
1. `docker compose ps` → `qlm-minecraft` 状态 `Up`
2. 日志出现：`预禁用冲突模组（ToughAsNails）` + `僵尸人口控制: ...已移除...`（首次部署后约 5 秒内）
3. 玩家能正常登录**不被踢**（原：登录即被 EncoderException 踢出）
4. **服务器运行超过 10 分钟不崩溃**（重点！之前 1-2 分钟就 watchdog 崩溃）
5. 玩家连接不再报「服务器缺少 Thirst was Taken」

## ⚠️ 部署后建议（可选，快速清掉存量僵尸）
首次启动后若想立刻清掉历史堆积的 3000+ 僵尸，可在控制台/RCON 执行：
```
kill @e[type=minecraft:zombie]
```
（之后人口控制会自动维持在 1500 只以内。）

## 已知但非阻塞（可选后续处理）
- 外置 Thirst 与集成口渴模块的 config 冲突（`thirst/item_settings.toml`）——
  已优雅降级（日志"口渴系统模块初始化失败，已跳过"），不崩溃；外置 Thirst 正常提供功能。
- 外置 Thirst 的额外战利品表（`brewinandchewin`/`farmersrespite` 物品）因未装对应
  模组而解析失败——纯日志噪音，不影响服务器。
- **watchdog 崩溃（内存）**：日志确认仍为 2G。若提升内存后仍偶发崩溃，可再降
  `VIEW_DISTANCE`（docker-compose 中 `MC_VIEW_DISTANCE=10` → 6~8）减轻负载。
