package com.qlm.zombie

import com.mojang.logging.LogUtils
import com.qlm.zombie.ai.Player2APIService
import com.qlm.zombie.block.QLMBlocks
import com.qlm.zombie.config.QLMConfig
import com.qlm.zombie.craftingdead.block.CDBlocks
import com.qlm.zombie.craftingdead.effect.CDEffects
import com.qlm.zombie.craftingdead.entity.CDEntities
import com.qlm.zombie.craftingdead.item.CDItems
import com.qlm.zombie.craftingdead.tab.CDCreativeTabs
import com.qlm.zombie.dependency.ModDependencyHandler
import com.qlm.zombie.effect.QLMEffects
import com.qlm.zombie.entity.QLMEntities
import com.qlm.zombie.item.QLMItems
import com.qlm.zombie.item.QLMTabs
import com.qlm.zombie.loot.QLMGlobalLootModifiers
import com.qlm.zombie.music.QLMSounds
import com.qlm.zombie.thirst.Thirst
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.loading.FMLEnvironment
import net.minecraft.network.chat.Component
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.AddReloadListenerEvent
import net.minecraftforge.event.entity.EntityAttributeCreationEvent
import net.minecraftforge.eventbus.api.IEventBus
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.ModList
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import org.slf4j.Logger

@Mod(QLMZombieMod.MOD_ID)
class QLMZombieMod {
    init {
        val modEventBus = FMLJavaModLoadingContext.get().modEventBus

        modEventBus.addListener(::commonSetup)

        // Core registrations
        QLMItems.register(modEventBus)
        QLMBlocks.register(modEventBus)
        QLMTabs.register(modEventBus)
        QLMSounds.register(modEventBus)
        QLMEffects.register(modEventBus)
        QLMGlobalLootModifiers.register(modEventBus)
        QLMEntities.register(modEventBus)

        // DropTheMeat codec registration
        com.qlm.zombie.feature.DropTheMeatLootModifier.registerCodecs(modEventBus)

        // Crafting Dead module registrations
        CDEffects.register(modEventBus)
        CDItems.register(modEventBus)
        CDEntities.register(modEventBus)
        CDBlocks.register(modEventBus)
        CDCreativeTabs.register(modEventBus)

        modEventBus.addListener(::registerEntityAttributes)

        // Note: DayPhaseManager, ZombieEvolutionHandler, AIPlayerChatHandler,
        // and StructureGenerators use @Mod.EventBusSubscriber for auto-registration
        // No need to manually register them here

        MinecraftForge.EVENT_BUS.addListener(::onAddReloadListener)
        MinecraftForge.EVENT_BUS.addListener(::onPlayerLogin)

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QLMConfig.SPEC, "qlmzombie-common.toml")

        // 口渴系统模块（Thirst was Taken 开源模组整合，MIT）
        // 注册物品/纹理、水质机制、口渴能力、HUD 与配置
        // 健壮性：模块初始化失败仅禁用口渴功能，绝不影响主模组启动
        try {
            Thirst.init(modEventBus)
        } catch (e: Throwable) {
            LOGGER.error("[QLM Zombie] 口渴系统模块初始化失败，已跳过（不影响其他功能）", e)
        }

        // Auto-release internal mods (extracts embedded jars to mods/ directory)
        // 白名单源：mod JAR 内 libs/ 目录（由 build.gradle.kts 从 src/main/libs/ 打包）
        // 重写版策略：精确白名单 + 自动恢复误禁用 + 保守禁用（仅 DEFAULT_DISABLED_PREFIXES）
        ModDependencyHandler.initializeFromLibs()

        // Check if optional dependencies are present after extraction.
        // ModDependencyHandler extracted jars during this init, but Forge has
        // already finished its mod scan for this launch. We check here and set
        // needsRestart = true so the user knows to restart next time.
        checkOptionalDependencies()
    }

    private fun checkOptionalDependencies() {
        val missing = mutableListOf<String>()

        // kotlinforforge: provides Kotlin Forge integration (Kotlin stdlib is bundled in our jar)
        if (!ModList.get().isLoaded("kotlinforforge")) {
            missing.add("kotlinforforge (Kotlin Forge 集成)")
        }

        // kubejs: provides JavaScript scripting engine
        if (!ModList.get().isLoaded("kubejs")) {
            missing.add("kubejs (KubeJS 脚本引擎)")
        }

        // cloth-config: provides config GUI
        if (!ModList.get().isLoaded("cloth-config")) {
            missing.add("cloth-config (配置界面)")
        }

        val restoredCount = ModDependencyHandler.getRestoredCount()
        val restoredMods = ModDependencyHandler.getRestoredMods()

        if (missing.isNotEmpty()) {
            LOGGER.warn("[QLM Zombie] ====== 缺少可选依赖模组 ======")
            LOGGER.warn("[QLM Zombie] 缺少 {} 个可选依赖: {}", missing.size, missing.joinToString(", "))
            LOGGER.warn("[QLM Zombie] ModDependencyHandler 已将它们释放到 mods/ 目录")
            LOGGER.warn("[QLM Zombie] 请重启游戏以加载这些依赖！")
            LOGGER.warn("[QLM Zombie] 本次启动核心功能仍可用 (Kotlin 运行时已内嵌)")
            LOGGER.warn("[QLM Zombie] ========================================")
            needsRestart = true
        } else {
            LOGGER.info("[QLM Zombie] 所有可选依赖均已加载 ✓")
        }

        if (restoredCount > 0) {
            LOGGER.warn("[QLM Zombie] ====== 自动恢复了被误禁用的模组 ======")
            LOGGER.warn("[QLM Zombie] 恢复 {} 个: {}", restoredCount, restoredMods.joinToString(", "))
            LOGGER.warn("[QLM Zombie] 这些模组已从 .disabled 状态恢复，请重启游戏以加载它们")
            LOGGER.warn("[QLM Zombie] ========================================")
            needsRestart = true
        }
    }

    private fun commonSetup(event: FMLCommonSetupEvent) {
        event.enqueueWork {
            LOGGER.info("[QLM Zombie] 七零喵僵尸末日生存mod (Kotlin版) v{} 已加载", MOD_VERSION)
            LOGGER.info("[QLM Zombie] 基于Kotlin+Java+KubeJS技术栈重构")
            LOGGER.info("[QLM Zombie] 开源仓库: https://github.com/SevenZeroMeowTeam/qlmzombie")
            LOGGER.info("[QLM Zombie] AI伴侣系统已启用")
            LOGGER.info("[QLM Zombie] 支持中文命令: 挖矿/砍树/收集/攻击/建造/制作/跟随/停下")

            if (FMLEnvironment.dist == Dist.CLIENT) {
                try {
                    val port = QLMConfig.apiPort
                    Player2APIService.getInstance(port).start()
                } catch (e: Exception) {
                    LOGGER.warn("[QLM Zombie] MCP API服务启动失败: ${e.message}")
                }
            }
        }
    }

    private fun onPlayerLogin(event: net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent) {
        val entity = event.entity ?: return

        if (needsRestart) {
            val restoredCount = ModDependencyHandler.getRestoredCount()
            if (restoredCount > 0) {
                val msg1 = "§c[七零喵] 检测到有 ${restoredCount} 个依赖模组被误禁用，已自动恢复！"
                val msg2 = "§c[七零喵] 请重启游戏以加载 kotlinforforge / kubejs / cloth-config 等依赖"
                val msg3 = "§e[七零喵] 本次启动核心功能 (物品/方块/实体/AI) 仍可使用"
                entity.sendSystemMessage(Component.literal(msg1))
                entity.sendSystemMessage(Component.literal(msg2))
                entity.sendSystemMessage(Component.literal(msg3))
            } else {
                val msg1 = "§c[七零喵] 检测到首次安装！内嵌依赖已释放到 mods/ 目录"
                val msg2 = "§c[七零喵] 请重启游戏以加载 kotlinforforge / kubejs / cloth-config 等依赖"
                val msg3 = "§e[七零喵] 本次启动核心功能 (物品/方块/实体/AI) 仍可使用"
                entity.sendSystemMessage(Component.literal(msg1))
                entity.sendSystemMessage(Component.literal(msg2))
                entity.sendSystemMessage(Component.literal(msg3))
            }
        }

        // 游戏公告
        entity.sendSystemMessage(Component.literal("§6§l[七零喵僵尸末日] §b§l v$MOD_VERSION §r§7- §a末日求生·品质时代"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7侧边栏计分板实时同步：☀天数 ☘安全日 ⌚时间 ☾月相 ⚔阶段 ❤生命 ⚔攻击"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7计分板每秒自动刷新，生命/攻击上限实时读取真实属性，击杀永久加成自动同步"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§710级装备品质：劣质→一般→普通→精良→优秀→稀有→卓越→史诗→传说→神话"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7动态难度：0-25天和平·26-50天简单·51-75天普通·76-100天困难·100天+锁定困难"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7神话级武器攻击力/耐久无限；神话盔甲套装缺一不可，全套无视虚空伤害、可破坏基岩"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7镐子能力：3×3~11×11范围挖掘，可破坏黑曜石/哭泣黑曜石/基岩"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7击杀敌对生物（除玩家/村民/铁傀儡）获得永久随机生命上限和攻击力上限"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7初始装备：铁质全套+5附魔+弓满附魔+64附魔金苹果+64面包"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7初始装备全部无限耐久：铁剑/斧/镐/锹/锄+弓+全套铁盔甲（Unbreakable）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7铁剑§c999§7/铁斧§c55§7/铁镐§c44§7/常规合成即可获得品质属性"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7合成时材料+月相影响品质；所有常规材料均有概率获得特殊属性"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7血量低于5%时自动触发生命恢复 III（60秒，冷却5分钟）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7血月系统：前25天安全期，之后每14天一次血月降临（血月僵尸进化概率翻倍）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7僵尸进化系统：随天数增加，僵尸进化概率和血量提升（血月进化更高）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7僵尸潮系统：每28天夜晚，5波僵尸潮+小Boss(死亡召唤精英僵尸)+大Boss 3阶段"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7新手保护：前25天不生成敌对生物，安心发育"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7维度封禁：下界/末地无法进入；末影人/苦力怕/女巫/猪灵族/蜘蛛/守卫者族已封禁"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7村民守卫：5%概率村民变守卫（100血/25攻），不逃跑+铁傀儡协助"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7自动扫描：每2秒扫描附近20格，僵尸按强度发光标记"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7成就系统：10项任务解锁成就，仅击杀敌对生物计数，奖励技能点"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7技能点：初始5点，成就奖励更多，使用 §b/qlm skill§r 查看"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7AI玩家背包：使用 §b/qlm backpack§r 打开背包（数据持久化）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7Boss死亡掉落宝箱：含原版稀有物品+其他模组随机物品"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7僵尸方块破坏/搭建：僵尸可破坏方块+搭建追击玩家"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7僵尸双手持物：25%概率手持物品，40%副手持有"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7昼夜行为：僵尸/骷髅白天不主动不燃烧，晚上加速，被攻击才反击"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7特殊骷髅（7种）：远程（凋零/剧毒/爆破/铁甲）×近战（剑士/狂战士/守卫）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7特殊僵尸（16种）：巨人(500血半血投小鬼)/木桶(死亡爆3-4小鬼)/召唤/烈焰/剧毒/铁甲/跳跃/投掷/吐息/爆破/投手TNT/自爆/弓箭手"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7下界合金锭：击杀敌对生物概率掉落（抢夺附魔提高概率，下界已封禁仅此来源）"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7废弃建筑：废墟/商店/加油站/9层高楼(每层箱子+楼梯可通行)/学校(5间房含床蜘蛛网箱子)/军事基地/海底遗迹"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7建筑箱子自动包含其他模组物品，无需手动注册"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7睡袋系统：3羊毛合成，夜晚可入睡不重置出生点，白天自动收起可拾取"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7血量UI：仅保留自定义绿色血量条+护甲/饱食度数值，原版心形隐藏"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7§l依赖释放 v4.2：§r§7crafting-dead 4模组升级（core 1.9.4.8 / decoration 1.0.6.8 / survival 1.2.5.8 / worldguard 0.0.6.8），119模组零重复"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7§l口渴系统 v5：§r§7整合 Thirst-Mod（MIT）——水质净化（脏→纯净）、陶碗喝水、熔炉/营火净化、脱水伤害"))
        entity.sendSystemMessage(Component.literal("§6[公告] §r§7输入 §a/qlm help§r 查看命令列表，§b/qlm mods§r 查看内嵌模组状态，§b/qlm download§r 强制重新释放"))
    }

    private fun onAddReloadListener(event: AddReloadListenerEvent) {
        event.addListener(object : net.minecraft.server.packs.resources.SimplePreparableReloadListener<Unit>() {
            override fun getName(): String = "qlmzombie_advancement_listener"

            override fun prepare(
                pResourceManager: net.minecraft.server.packs.resources.ResourceManager,
                pProfiler: net.minecraft.util.profiling.ProfilerFiller
            ) {}

            override fun apply(
                pObject: Unit,
                pResourceManager: net.minecraft.server.packs.resources.ResourceManager,
                pProfiler: net.minecraft.util.profiling.ProfilerFiller
            ) {
                LOGGER.info("[QLM Zombie] 成就系统已加载")
            }
        })
    }

    private fun registerEntityAttributes(event: EntityAttributeCreationEvent) {
        QLMEntities.registerAttributes(event)
        CDEntities.registerAttributes(event)
    }

    companion object {
        const val MOD_ID = "qlmzombie"
        @JvmField
        val LOGGER: Logger = LogUtils.getLogger()
        const val MOD_VERSION = "3.0.0.beta.build37"

        @JvmField
        @Volatile
        var needsRestart = false
    }
}
