'use strict';

/**
 * 背包管理器 InventoryManager
 *
 * 功能:
 *  - 自动切换最佳工具（依据方块类型）
 *  - 工具损坏自动替换
 *  - 自动丢弃垃圾物品
 *  - 查询物品数量
 */
const { getLogger } = require('../utils/logger');

// 方块 → 最佳工具 映射
const BLOCK_TOOL_MAP = {
  // 镐
  stone: 'pickaxe',
  cobblestone: 'pickaxe',
  granite: 'pickaxe',
  diorite: 'pickaxe',
  andesite: 'pickaxe',
  iron_ore: 'pickaxe',
  deepslate_iron_ore: 'pickaxe',
  coal_ore: 'pickaxe',
  deepslate_coal_ore: 'pickaxe',
  copper_ore: 'pickaxe',
  deepslate_copper_ore: 'pickaxe',
  gold_ore: 'pickaxe',
  deepslate_gold_ore: 'pickaxe',
  diamond_ore: 'pickaxe',
  deepslate_diamond_ore: 'pickaxe',
  emerald_ore: 'pickaxe',
  deepslate_emerald_ore: 'pickaxe',
  redstone_ore: 'pickaxe',
  deepslate_redstone_ore: 'pickaxe',
  lapis_ore: 'pickaxe',
  deepslate_lapis_ore: 'pickaxe',
  netherrack: 'pickaxe',
  basalt: 'pickaxe',
  blackstone: 'pickaxe',
  obsidian: 'pickaxe',
  deepslate: 'pickaxe',
  // 斧
  oak_log: 'axe',
  spruce_log: 'axe',
  birch_log: 'axe',
  jungle_log: 'axe',
  acacia_log: 'axe',
  dark_oak_log: 'axe',
  mangrove_log: 'axe',
  cherry_log: 'axe',
  oak_planks: 'axe',
  spruce_planks: 'axe',
  birch_planks: 'axe',
  jungle_planks: 'axe',
  acacia_planks: 'axe',
  dark_oak_planks: 'axe',
  crafting_table: 'axe',
  chest: 'axe',
  bookshelf: 'axe',
  // 铲
  dirt: 'shovel',
  grass_block: 'shovel',
  sand: 'shovel',
  gravel: 'shovel',
  clay: 'shovel',
  snow_block: 'shovel',
  soul_sand: 'shovel',
  // 剪刀
  grass: 'shears',
  tall_grass: 'shears',
  fern: 'shears',
  vine: 'shears',
  leaves: 'shears',
  oak_leaves: 'shears',
  spruce_leaves: 'shears',
  birch_leaves: 'shears'
};

// 工具等级（数字越大越好）
const TOOL_TIER = {
  wooden: 1, stone: 2, golden: 3, iron: 4, diamond: 5, netherite: 6
};

class InventoryManager {
  /**
   * @param {import('mineflayer').Bot} bot
   * @param {object} config
   * @param {Memory} memory
   */
  constructor(bot, config = {}, memory) {
    this.bot = bot;
    this.config = config.inventory || config;
    this.memory = memory;
    this.log = getLogger(config).child({ module: 'Inventory' });
    this.trashItems = new Set(this.config.trashItems || []);
  }

  /** 同步背包到记忆 */
  sync() {
    if (this.memory) this.memory.updateInventory(this.bot);
  }

  /** 查询物品数量 */
  itemCount(name) {
    return this.bot.inventory.items()
      .filter(i => i.name === name)
      .reduce((sum, i) => sum + i.count, 0);
  }

  /** 是否拥有物品 */
  hasItem(name, count = 1) {
    return this.itemCount(name) >= count;
  }

  /** 查找指定名称的物品 */
  findItem(name) {
    return this.bot.inventory.items().find(i => i.name === name) || null;
  }

  /** 查找所有匹配名称的物品 */
  findItems(name) {
    return this.bot.inventory.items().filter(i => i.name === name);
  }

  /**
   * 找到最适合挖掘该方块的工具
   * @returns {import('prismarine-item').Item | null}
   */
  findBestToolForBlock(blockName) {
    const toolType = BLOCK_TOOL_MAP[blockName];
    if (!toolType) return null;

    const candidates = this.bot.inventory.items().filter(item => {
      // 工具命名: wooden_pickaxe, stone_pickaxe, iron_pickaxe, diamond_pickaxe, netherite_pickaxe
      return item.name.endsWith('_' + toolType) || item.name === toolType;
    });
    if (candidates.length === 0) return null;

    // 选择等级最高、耐久最多的工具
    candidates.sort((a, b) => {
      const ta = this.toolTier(a.name);
      const tb = this.toolTier(b.name);
      if (tb !== ta) return tb - ta;
      // 同等级选耐久高的
      const da = (a.maxDurability || 0) - (a.nbt ? (a.nbt.Damage || 0) : 0);
      const db = (b.maxDurability || 0) - (b.nbt ? (b.nbt.Damage || 0) : 0);
      return db - da;
    });
    return candidates[0];
  }

  /** 获取工具等级 */
  toolTier(itemName) {
    for (const [material, tier] of Object.entries(TOOL_TIER)) {
      if (itemName.startsWith(material + '_')) return tier;
    }
    return 0;
  }

  /** 装备物品到主手 */
  async equip(item) {
    if (!item) return false;
    if (this.bot.heldItem && this.bot.heldItem.name === item.name) return true;
    try {
      await this.bot.equip(item, 'hand');
      return true;
    } catch (e) {
      this.log.warn(`装备 ${item.name} 失败: ${e.message}`);
      return false;
    }
  }

  /** 装备最佳工具来挖掘指定方块 */
  async equipToolForBlock(blockName) {
    if (!this.config.autoTool) return true;
    const tool = this.findBestToolForBlock(blockName);
    if (tool) {
      return await this.equip(tool);
    }
    return true; // 没有工具也允许尝试手挖
  }

  /** 装备武器（剑或斧） */
  async equipWeapon() {
    if (!this.config.autoTool) return true;
    const swords = this.bot.inventory.items().filter(i => i.name.endsWith('_sword'));
    if (swords.length > 0) {
      swords.sort((a, b) => this.toolTier(b.name) - this.toolTier(a.name));
      return await this.equip(swords[0]);
    }
    // 兜底用斧头
    const axes = this.bot.inventory.items().filter(i => i.name.endsWith('_axe'));
    if (axes.length > 0) {
      axes.sort((a, b) => this.toolTier(b.name) - this.toolTier(a.name));
      return await this.equip(axes[0]);
    }
    return true;
  }

  /** 检查手持工具是否快损坏（耐久 < 5%） */
  isHeldToolLowDurability() {
    const item = this.bot.heldItem;
    if (!item || !item.maxDurability) return false;
    const used = item.nbt && item.nbt.Damage ? item.nbt.Damage : 0;
    const remaining = item.maxDurability - used;
    return remaining < item.maxDurability * 0.05;
  }

  /** 切换到耐久更高的同类工具 */
  async switchToBetterToolIfLow() {
    if (!this.isHeldToolLowDurability()) return false;
    const held = this.bot.heldItem;
    if (!held) return false;

    // 找同类工具
    const baseName = held.name.split('_').slice(1).join('_');
    const alternatives = this.bot.inventory.items().filter(i =>
      i.name.endsWith(baseName) && i.name !== held.name && i.maxDurability
    );
    if (alternatives.length === 0) return false;

    alternatives.sort((a, b) => this.toolTier(b.name) - this.toolTier(a.name));
    await this.equip(alternatives[0]);
    this.log.info(`工具损坏前切换: ${held.name} → ${alternatives[0].name}`);
    return true;
  }

  /** 丢弃垃圾物品 */
  async dropTrash() {
    if (!this.config.dropTrash) return;
    const trash = this.bot.inventory.items().filter(i => this.trashItems.has(i.name));
    for (const item of trash) {
      try {
        await this.bot.tossStack(item);
        this.log.debug(`丢弃 ${item.count}x ${item.name}`);
      } catch (e) {
        this.log.warn(`丢弃 ${item.name} 失败: ${e.message}`);
      }
    }
  }

  /** 丢弃指定物品 */
  async dropItem(name, count = 1) {
    const item = this.findItem(name);
    if (!item) return false;
    try {
      await this.bot.toss(item.type, null, count);
      return true;
    } catch (e) {
      return false;
    }
  }

  /** 是否有空槽位 */
  hasEmptySlot() {
    return this.bot.inventory.emptySlotCount() > 0;
  }

  /** 获取所有食物 */
  getFoods() {
    return this.bot.inventory.items().filter(i => i.food);
  }
}

module.exports = { InventoryManager, BLOCK_TOOL_MAP, TOOL_TIER };
