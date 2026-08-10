package com.qlm.zombie;

import com.mojang.logging.LogUtils;
import com.qlm.zombie.cloudai.event.EventBusSubscriber;
import com.qlm.zombie.config.QLMConfig;
import com.qlm.zombie.craftingdead.block.CDBlocks;
import com.qlm.zombie.craftingdead.effect.CDEffects;
import com.qlm.zombie.craftingdead.entity.CDEntities;
import com.qlm.zombie.craftingdead.entity.zombie.CivilianZombie;
import com.qlm.zombie.craftingdead.entity.zombie.ScientistZombie;
import com.qlm.zombie.craftingdead.entity.zombie.SoldierZombie;
import com.qlm.zombie.craftingdead.item.CDItems;
import com.qlm.zombie.craftingdead.tab.CDCreativeTabs;
import com.qlm.zombie.dayphase.DayPhaseManager;
import com.qlm.zombie.dependency.ModDependencyHandler;
import com.qlm.zombie.entity.FakePlayerEntity;
import com.qlm.zombie.entity.GiantZombieEntity;
import com.qlm.zombie.entity.QLMEntities;
import com.qlm.zombie.item.QLMItems;
import com.qlm.zombie.item.QLMTabs;
import com.qlm.zombie.ai.Player2APIService;
import com.qlm.zombie.loot.QLMGlobalLootModifiers;
import com.qlm.zombie.music.QLMSounds;
import com.qlm.zombie.player.AIPlayerChatHandler;
import com.qlm.zombie.zombie.ZombieEvolutionHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// 注: "qlmzombie" 为资源目录 (data/qlmzombie / assets/qlmzombie) 与 mods.toml 的 modId
// 常量名使用 MOD_ID 而非 MODID，避免 IDE 拼写检查误报
@Mod(QLMZombieMod.MOD_ID)
public class QLMZombieMod {

    public static final String MOD_ID = "qlmzombie";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final String MOD_VERSION = "2.10.0.rewrite.beta.build.38.0";

    public static boolean needsRestart = false;

    @SuppressWarnings("removal")
    public QLMZombieMod(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        modEventBus.addListener(this::commonSetup);

        QLMItems.ITEMS.register(modEventBus);
        QLMTabs.TABS.register(modEventBus);
        QLMSounds.register(modEventBus);
        QLMGlobalLootModifiers.LOOT_MODIFIER_SERIALIZERS.register(modEventBus);
        QLMEntities.ENTITY_TYPES.register(modEventBus);
        // DropTheMeat 玩法的 GLM Codec（原创实现，参考开源设计模式）
        com.qlm.zombie.feature.DropTheMeatLootModifier.GLM_CODECS.register(modEventBus);
        // CloudAI Follower 模块绑定（DeferredRegister + Mod生命周期事件）
        EventBusSubscriber.onModConstruct(context);

        // ========== Crafting Dead 模块注册 ==========
        CDEffects.MOB_EFFECTS.register(modEventBus);
        CDItems.ITEMS.register(modEventBus);
        CDEntities.ENTITY_TYPES.register(modEventBus);
        CDBlocks.BLOCKS.register(modEventBus);
        CDBlocks.BLOCK_ENTITIES.register(modEventBus);
        CDBlocks.BLOCK_ITEM_REGISTER.register(modEventBus);
        CDCreativeTabs.TABS.register(modEventBus);

        modEventBus.addListener(this::registerEntityAttributes);

        // 以下类已通过 @Mod.EventBusSubscriber 自动注册到 Forge 事件总线
        // 此处显式注册作为双保险，确保事件监听器正常工作
        MinecraftForge.EVENT_BUS.register(DayPhaseManager.class);
        MinecraftForge.EVENT_BUS.register(ZombieEvolutionHandler.class);
        MinecraftForge.EVENT_BUS.register(com.qlm.zombie.structure.AbandonedShopGenerator.class);
        MinecraftForge.EVENT_BUS.register(AIPlayerChatHandler.class);
        MinecraftForge.EVENT_BUS.addListener(this::onAddReloadListener);
        MinecraftForge.EVENT_BUS.addListener(this::onPlayerLogin);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, QLMConfig.SPEC, "qlmzombie-common.toml");

        // 从内部 libs 自动释放所有 mod，并检测冲突
        ModDependencyHandler.initializeFromLibs();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("[QLM Zombie] 七零喵僵尸末日生存mod v{} 已加载", MOD_VERSION);
            LOGGER.info("[QLM Zombie] 建筑物宝箱已注入战利品（TaCZ 火器 / Spartan 装备 / 原版工具/防具 / QLM 自制物资），包括各种枪械、弹药、配件、近战武器与防具。");
            LOGGER.info("[QLM Zombie] 废弃商店已启用：主世界随机生成废弃商店结构，奖励箱放置在货架上，有概率生成mod物品。");
            LOGGER.info("[QLM Zombie] 巨人僵尸已加入尸潮最终波次！当巨人僵尸血量低于50%时，会投掷小鬼僵尸！");
            LOGGER.info("[QLM Zombie] AI玩家聊天交互系统已启用：可通过聊天指令与AI玩家交流，支持Player2 MCP API");
            LOGGER.info("[QLM Zombie] v2.2.0 新增: 武器/工具/盔甲合成品质系统（7档品质，伤害随机，神话级可破坏基岩、无视游戏规则限制）");
            LOGGER.info("[QLM Zombie] v2.3.0 新增: AI玩家重写（Player2 MCP API远程指令执行）、动态难度怪物AI增强（僵尸/骷髅随天数变强）");
            LOGGER.info("[QLM Zombie] v2.4.0 新增: AI物品注册表（200+中文物品名映射）、任务完成自动跟随、执行任务时不跟随仅驯服AI生效");
            LOGGER.info("[QLM Zombie] v2.5.0 新增: AI玩家建造系统（5x5小屋蓝图）、AI制作系统（RecipeManager配方解析）、好感度驯服系统（食物投喂0-100）、AI自由活动（未驯服漫步+好奇靠近玩家）");
            LOGGER.info("[QLM Zombie] v2.6.0 新增: README文档全面同步、AI建造/制作/驯服/自由活动功能说明完善、游戏内公告优化");
            LOGGER.info("[QLM Zombie] v2.7.0 修复: 废弃商店奖励箱物资生成(修复enchant_randomly格式+GLM必填字段+动态注册23个模组命名空间)、AI玩家任务执行冲突(5层防线拦截MeleeAttackGoal/TargetGoal抢占导航)");
            LOGGER.info("[QLM Zombie] v2.8.0 维护: 全项目代码审计清理(清除BuiltInRegistries弃用API→统一使用ForgeRegistries.ITEM)、移除FakePlayerEntity重复导入、0编译警告0诊断错误");
            LOGGER.info("[QLM Zombie] v2.9.0 修复: AI玩家指令未执行就结束跟随(3层根因修复:事件显式注册+最小任务时长保护+收集任务智能回退砍树/挖矿)、指令解析误匹配修复(来/打等宽泛单字不再截胡具体指令)、异步线程安全(server.execute主线程调度)");
            LOGGER.info("[QLM Zombie] v2.10.0 架构重构: 参考PlayerEngine/Player2NPC，AI玩家全架构重构(Task抽象基类+TaskRunner生命周期管理+TaskCatalogue命令映射+14个模块化任务类+CompanionManager+能力接口)，单体AIPlayerChatHandler从1443行→163行");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.2.0 开源重写: 基于117个GitHub开源仓库清单全面审查，PlayerEngine/Player2NPC相关26个核心文件添加开源署名头，确认代码为原创Forge 1.20.1实现，libs自动释放机制修复，游戏加载无报错");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.3.0 多AI指令冲突修复: 冷却机制改为玩家UUID+AI UUID独立冷却，AI选择逻辑支持@名字精确匹配+模糊匹配，指令格式优化支持多种写法");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.4.0 Player2NPC兼容修复: 聊天事件不再拦截其他模组NPC指令，API会话隔离(qlm_前缀)，静默返回不干扰其他模组");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.5.0 AI任务执行修复+MCP集成: Goal系统不再抢Task导航控制权，新增/qlm mcp命令显示MCP服务器配置");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.6.0 新增一键砍木板斧: 右键将物品栏所有原木一次性转换为木板，支持原版和其他模组原木(通过合成配方自动查找)");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.7.0 新增一键收集木板斧: 右键收集附近16格内所有木板掉落物，支持原版和其他模组木板(通过planks标签识别)");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.8.0 新增连锁砍木板+AI收集木板任务: 连锁挖矿系统新增斧头砍木板连锁破坏，AI玩家新增收集木板指令");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.9.0 新增随机建筑物生成: 世界生成时随机生成小屋/瞭望塔/废墟，含战利品箱子");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.10.0 随机建筑物箱子注入其他模组物品: TACZ武器/Spartan武器/23个模组命名空间自动扫描注入");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.22.0 新增口渴模式：原版水瓶无法直接饮用(需熔炉加热为纯净水)，玩家会随时间增加口渴值，过高会脱水(缓慢/疲劳)，饮用纯净水可恢复口渴值");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.23.0 口渴模式重写：基于Thirst-Mod架构采用exhaustion衰减模型，跑动/跳跃/挖掘积累消耗值，满值增加口渴，口渴归零扣血，雨天仰望天空自动补水，饮用纯净水恢复口渴值并返还空瓶");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.24.0 口渴HUD+开源mod：经验条样式口渴条(饱食度上方+像素水滴图标)，保留并自动释放ThirstWasTaken/ThirstCanteen开源JAR，移除脱水负面效果(缓慢/疲劳)");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.25.0 口渴debuff修复：仅口渴值<=6时保留挖掘疲劳/缓慢，>6时自动清除，避免加入游戏即被施加debuff");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.26.0 Python脚本引擎+性能优化：集成Jython/GraalPy/Jep三引擎，scripts/python/自动加载.py脚本，qlm API暴露Forge事件；优化tick节流(debuff检查40tick/成就检查100tick)和HUD渲染缓存");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.27.0 qlm API扩充：方块操作(getBlock/setBlock/breakBlock/getBlockRange)，实体生成(spawnEntity/spawnEntityBatch/getNearbyEntities/removeEntity)，Forge事件桥接(onBlockBreak/onEntityDeath)");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.28.0 GraalPy集成：build.gradle直接依赖org.graalvm.polyglot:polyglot+python 23.1.0，三引擎(GraalPy/Jython/Jep)全部开箱即用，无需手动配置JAR");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.29.0 崩溃修复：移除Jython/GraalPy编译依赖(org.w3c.dom.html与JDK jdk.xml.dom模块冲突)，Jython改为src/libs释放；移除ThirstCanteen 3.6(与ThirstWasTaken 1.4.0包名不兼容)");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.30.0 模块冲突彻底修复：排除libs/中Python引擎原始JAR(jython/graal/polyglot)，仅保留implementation依赖提取的类，彻底解决JPMS模块冲突");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.35.0 FakePlayer崩溃修复: FakePlayer饥饿掉血时触发Footwork/Mekanism的LivingHurtEvent，ItemCapabilityWrapper.getCapability因capability为null抛出NullPointerException导致服务端崩溃。修复方案：(1)FakePlayer不再饥饿掉血，改为自动恢复饱食度至20；(2)hurt方法添加try-catch安全网，捕获capability检查NPE返回false，防止任何伤害源的capability检查崩溃");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.34.0 严重崩溃修复: 随机建筑/废弃商店生成器在ChunkEvent.Load中调用level.getHeight()导致区块加载死锁，服务器tick超60秒被Watchdog强制关闭，AI Bot因服务器无响应而超时断开。改用区块自身高度图chunk.getHeight(WORLD_SURFACE)并延迟到下一tick执行建筑生成");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.33.0 LLM大模型接入+AI修复+自动搭建: Node.js LLMBridge自然语言转任务JSON(!ai指令)，Mod内部AI LLMBridge.java异步规划，TaskRunner任务链串行执行，AI自动搭建方块收集高处物品，Navigator/FSMBrain原地打转修复");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.36.0 Crafting Dead 末日装备系统移植：42+ 物品（8枪/7弹/13附件/3近战/3手雷/4防具/2方块）+ 3僵尸变种 + 4创意标签页，全部 cd_ 前缀，forge DeferredRegister 注册，compileJava 0 错误");
            LOGGER.info("[QLM Zombie] Crafting Dead 模块注册完成：CDEffects(5效果)/CDItems(42+物品)/CDEntities(4实体)/CDBlocks(2方块+1方块实体)/CDCreativeTabs(4标签页) 五大 DeferredRegister 已接入");
            LOGGER.info("[QLM Zombie] Crafting Dead 模块内容：医疗8物、枪械AK47/M4A1/MP5/M1014/DesertEagle/Glock17/BarrettM82/AWM、4镜3握3枪管3弹匣、战斗刀/博伊刀/撬棍、破片/闪光/燃烧弹、防弹衣/头盔/背心/靴子、军人/科学家/平民僵尸、医疗补给箱/弹药箱方块");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.37.0 镐子随机能力系统：合成镐子有概率获得黑曜石破坏者(15%)/3x3范围挖掘(10%)/5x5范围挖掘(5%)，可叠加，NBT bitmask存储");
            LOGGER.info("[QLM Zombie] v2.10.0.rewrite.beta.build.38.0 9层高楼建筑系统：13×9高楼/5房间每层/每层奖励箱，15%概率注入其他模组物品，建筑不重复（区块坐标去重）");

            if (Player2APIService.isPlayer2Available()) {
                LOGGER.info("[QLM Zombie] Player2 MCP API 服务已连接，AI玩家可通过远程API执行智能任务");
            } else {
                LOGGER.info("[QLM Zombie] Player2 MCP API 服务未连接，AI玩家使用内置命令解析");
            }

            if (needsRestart) {
                LOGGER.warn("[QLM Zombie] ================================================");
                LOGGER.warn("[QLM Zombie] 依赖mod已释放到 mods 目录，请重启游戏！");
                LOGGER.warn("[QLM Zombie] ================================================");
            }

            // 初始化 Python 脚本引擎
            try {
                com.qlm.zombie.script.PythonScriptEngine.initialize(
                        net.minecraftforge.common.MinecraftForge.EVENT_BUS);
                LOGGER.info("[QLM Zombie] Python 脚本引擎: {}",
                        com.qlm.zombie.script.PythonScriptEngine.getActiveEngineName());
            } catch (Exception e) {
                LOGGER.warn("[QLM Zombie] Python 脚本引擎初始化失败: {}", e.getMessage());
            }
        });
    }

    private void onPlayerLogin(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() == null) return;

        int total = ModDependencyHandler.getTotalLibsCount();
        int released = ModDependencyHandler.getReleasedCount();
        boolean hasConflicts = ModDependencyHandler.hasConflicts();
        int disabled = ModDependencyHandler.getDisabledMods().size();

        if (needsRestart) {
            String msg = "§e[七零喵团队 SevenZeroMeowTeam] §c需要重启游戏以加载新安装的mod！";
            if (total > 0) {
                msg += " §7(内部mod " + total + "个，已释放 " + released + "个";
                if (hasConflicts) {
                    msg += "，检测到冲突mod " + disabled + "个已禁用";
                }
                msg += ")";
            }
            event.getEntity().sendSystemMessage(Component.literal(msg));
        }

        event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] v" + MOD_VERSION + " §b9层高楼建筑系统上线！13×9高楼，5房间每层，每层奖励箱"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §515%概率奖励箱注入其他模组物品：§bTaCZ枪械/SpartanWeaponry近战/Create/Mekanism/Botania等29个模组"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a建筑不重复：区块坐标去重，每层奖励箱在不同房间循环放置，动态扫描模组命名空间"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] v" + MOD_VERSION + " §a镐子随机能力系统上线！合成镐子有概率获得特殊能力"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §d黑曜石破坏者(15%)：§b左键黑曜石/哭泣黑曜石直接破坏+掉落物，任何品质镐子均可"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §b3x3范围挖掘(10%)：§b破坏方块时以面向平面为中心3x3同种方块连锁"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §65x5范围挖掘(5%)：§b同上5x5范围，可叠加多能力，Tooltip显示✦标记"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCrafting Dead 末日装备系统上线！42+ 新物品（枪械/弹药/医疗/防具/近战/手雷/方块）"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCD战斗页：§bAK47/M4A1/MP5/M1014/沙鹰/Glock17/BarrettM82/AWM 8枪 + 4镜3握3枪管3弹匣13附件 + 战斗刀博伊刀撬棍 + 破片闪光燃烧弹"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCD医疗页：§b绷带/急救包/肾上腺素/止痛药/止血带/生理盐水袋/夹板/手术剪刀（5自定义效果：流血/骨折/肾上腺素/止痛/重度感染）"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCD装备页：§b防弹头盔/插板防弹衣/战术背心/作战靴（自定义CDArmorMaterial 头3胸8腿6靴3 韧性1.0）"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCD方块页：§b医疗补给箱（右键随机医疗物）+ 弹药箱（1-3种随机弹药×8-32发），60%保留为刷新点"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aCD僵尸变种：§b军人僵尸（35血+铁装+流血光环）/科学家僵尸（25血+毒反伤）/平民僵尸（弱化版）"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §c修复FakePlayer饥饿掉血NPE崩溃！Footwork/Mekanism capability检查不再崩服！"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §c崩溃修复：§bFakePlayer不再饥饿掉血(自动恢复饱食度)，hurt方法添加try-catch安全网防止capability NPE"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aAI Bot修复：§bForge握手ModData不再发送多余Ack，消除服务器'Recieved unexpected index'警告"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aAI随机形象：§b从 https://littleskin.cn/skinlib 抓取热门皮肤，40+ 内置兜底（苦力怕娘/蔡徐坤/胡桃/miku/GawrGura…）"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aURL皮肤修复：§bFakePlayerEntityRenderer HTTP 下载 bug 修复，setSkinURL(url) 现在能真的加载皮肤"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a外部AI机器人：§bnode-bot 目录，mineflayer+pathfinder，FSM/行为树/GOAP 三大脑可切换"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a多层架构：§b感知层/记忆层/决策层/行为层/执行层，动作锁保证串行执行"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a任务系统：§b串行队列，挖矿→合成木镐→收集圆石预设链，!help 查看指令"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a命令修复：§bspawn/tame/tp/kill支持中文名，不再报参数分隔错误"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aAI玩家：§b聊天指令控制，支持挖矿/砍树/收集/攻击/建造/制作等任务"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a装备系统：§b右键AI装备武器/盔甲/工具，任务自动切换镐/斧"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a背包GUI：§b空手右键打开AI背包，含盔甲槽+副手槽"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a玩法替代：§b吃不停+肉多多x5+骷髅AI修复+快速熔炉/工作台/配方"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §aPlayer2NPC兼容：§bcharacter_id前缀隔离，不拦截原版NPC指令"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §a自动释放：§b内部libs目录JAR自动释放到mods目录，启动即用"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §e动态难度：§b僵尸/骷髅随天数增强，血月每14天一次"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §e废弃商店：§b主世界随机生成，奖励箱含23个模组物品"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §e随机建筑：§b小屋/瞭望塔/废墟，箱子含23个模组物品注入"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §c巨人僵尸：§b加入尸潮最终波次，血量<50%投掷小鬼僵尸"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §d合成品质：§b武器/工具/盔甲随机7档品质，神话级可破坏基岩"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §d好感度驯服：§b喂食AI食物提升信任度(0-100)，满100驯服"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §c口渴系统：§b饱食度上方蓝色口渴条+像素水滴图标，仅口渴值<=6时才上debuff，雨天补水，纯净水饮用，自动释放ThirstWasTaken"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §9Python脚本：§bJython(内置)/Jep(pip install)/GraalPy(可选)，方块/实体/事件API，scripts/python/放.py自动加载"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §bLLM大模型：§b!ai <自然语言> 让AI理解指令(\"帮我建房子\"→任务链)，Mod内部AI也支持聊天自然语言指令"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §bAI自动搭建：§b高处物品/方块→自动搭方块柱上去收集/挖掘，背包需有可搭建方块"));
            event.getEntity().sendSystemMessage(Component.literal("§6[七零喵僵尸末日] §bAI修复：§bNavigator/FSMBrain原地打转修复，脱困机制(跳跃+侧向移动)，寻路失败自动重建路径"));
    }

    private void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) (ResourceManager resourceManager) -> {
            LOGGER.info("[QLM Zombie] 成就系统已加载");
        });
    }

    private void registerEntityAttributes(final EntityAttributeCreationEvent event) {
        event.put(QLMEntities.FAKE_PLAYER.get(), FakePlayerEntity.createAttributes().build());
        event.put(QLMEntities.GIANT_ZOMBIE.get(), GiantZombieEntity.createAttributes().build());
        // Crafting Dead 僵尸变种实体属性
        event.put(CDEntities.SOLDIER_ZOMBIE.get(), SoldierZombie.createAttributes().build());
        event.put(CDEntities.SCIENTIST_ZOMBIE.get(), ScientistZombie.createAttributes().build());
        event.put(CDEntities.CIVILIAN_ZOMBIE.get(), CivilianZombie.createAttributes().build());
    }
}