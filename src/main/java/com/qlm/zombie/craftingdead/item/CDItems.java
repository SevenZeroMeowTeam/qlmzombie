package com.qlm.zombie.craftingdead.item;

import com.qlm.zombie.QLMZombieMod;
import com.qlm.zombie.craftingdead.item.gun.*;
import com.qlm.zombie.craftingdead.item.medical.*;
import com.qlm.zombie.craftingdead.item.melee.*;
import com.qlm.zombie.craftingdead.item.grenade.*;
import com.qlm.zombie.craftingdead.item.armor.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumSet;

public class CDItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, QLMZombieMod.MOD_ID);

    // ==================== 医疗物品 ====================
    public static final RegistryObject<Item> CD_BANDAGE = ITEMS.register("cd_bandage", BandageItem::new);
    public static final RegistryObject<Item> CD_FIRST_AID_KIT = ITEMS.register("cd_first_aid_kit", FirstAidKitItem::new);
    public static final RegistryObject<Item> CD_ADRENALINE_SYRINGE = ITEMS.register("cd_adrenaline_syringe", AdrenalineSyringeItem::new);
    public static final RegistryObject<Item> CD_PAINKILLERS = ITEMS.register("cd_painkillers", PainkillersItem::new);
    public static final RegistryObject<Item> CD_TOURNIQUET = ITEMS.register("cd_tourniquet", TourniquetItem::new);
    public static final RegistryObject<Item> CD_SALINE_BAG = ITEMS.register("cd_saline_bag", SalineBagItem::new);
    public static final RegistryObject<Item> CD_SPLINT = ITEMS.register("cd_splint", SplintItem::new);
    public static final RegistryObject<Item> CD_SURGICAL_SCISSORS = ITEMS.register("cd_surgical_scissors", SurgicalScissorsItem::new);

    // ==================== 枪械物品 ====================
    public static final RegistryObject<Item> CD_AK47 = ITEMS.register("cd_ak47",
            () -> new AbstractGunItem(AmmoType._762x39, 10, 5.0F, 30, 0.12F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.GRIP, IGun.SlotType.MAGAZINE),
                    Rarity.RARE) {});
    public static final RegistryObject<Item> CD_M4A1 = ITEMS.register("cd_m4a1",
            () -> new AbstractGunItem(AmmoType._556x45, 7, 4.5F, 30, 0.09F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.GRIP, IGun.SlotType.BARREL, IGun.SlotType.MAGAZINE),
                    Rarity.RARE) {});
    public static final RegistryObject<Item> CD_MP5 = ITEMS.register("cd_mp5",
            () -> new AbstractGunItem(AmmoType._9x19, 5, 3.0F, 30, 0.08F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.GRIP, IGun.SlotType.BARREL, IGun.SlotType.MAGAZINE),
                    Rarity.UNCOMMON) {});
    public static final RegistryObject<Item> CD_M1014 = ITEMS.register("cd_m1014",
            () -> new AbstractGunItem(AmmoType._12_Gauge, 14, 6.0F, 6, 0.18F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.GRIP),
                    Rarity.UNCOMMON) {});
    public static final RegistryObject<Item> CD_DESERT_EAGLE = ITEMS.register("cd_desert_eagle",
            () -> new AbstractGunItem(AmmoType._45_ACP, 16, 7.0F, 7, 0.10F,
                    EnumSet.of(IGun.SlotType.BARREL, IGun.SlotType.MAGAZINE),
                    Rarity.RARE) {});
    public static final RegistryObject<Item> CD_BARRETT_M82 = ITEMS.register("cd_barrett_m82",
            () -> new AbstractGunItem(AmmoType._50_BMG, 28, 18.0F, 10, 0.05F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.BIPOD, IGun.SlotType.BARREL, IGun.SlotType.MAGAZINE),
                    Rarity.EPIC) {});
    public static final RegistryObject<Item> CD_AWM = ITEMS.register("cd_awm",
            () -> new AbstractGunItem(AmmoType._338_Lapua, 22, 14.0F, 5, 0.03F,
                    EnumSet.of(IGun.SlotType.SIGHT, IGun.SlotType.BIPOD, IGun.SlotType.MAGAZINE),
                    Rarity.EPIC) {});
    public static final RegistryObject<Item> CD_GLOCK17 = ITEMS.register("cd_glock17",
            () -> new AbstractGunItem(AmmoType._9x19, 8, 3.5F, 17, 0.11F,
                    EnumSet.of(IGun.SlotType.BARREL, IGun.SlotType.MAGAZINE),
                    Rarity.COMMON) {});

    // ==================== 弹药物品 ====================
    public static final RegistryObject<Item> CD_AMMO_556X45 = ITEMS.register("cd_ammo_556x45",
            () -> new AmmoItem(AmmoType._556x45));
    public static final RegistryObject<Item> CD_AMMO_762X39 = ITEMS.register("cd_ammo_762x39",
            () -> new AmmoItem(AmmoType._762x39));
    public static final RegistryObject<Item> CD_AMMO_9X19 = ITEMS.register("cd_ammo_9x19",
            () -> new AmmoItem(AmmoType._9x19));
    public static final RegistryObject<Item> CD_AMMO_45_ACP = ITEMS.register("cd_ammo_45_acp",
            () -> new AmmoItem(AmmoType._45_ACP));
    public static final RegistryObject<Item> CD_AMMO_50_BMG = ITEMS.register("cd_ammo_50_bmg",
            () -> new AmmoItem(AmmoType._50_BMG));
    public static final RegistryObject<Item> CD_AMMO_12_GAUGE = ITEMS.register("cd_ammo_12_gauge",
            () -> new AmmoItem(AmmoType._12_Gauge));
    public static final RegistryObject<Item> CD_AMMO_338_LAPUA = ITEMS.register("cd_ammo_338_lapua",
            () -> new AmmoItem(AmmoType._338_Lapua));

    // ==================== 瞄准镜附件 ====================
    public static final RegistryObject<Item> CD_SIGHT_RED_DOT = ITEMS.register("cd_sight_red_dot",
            () -> new SightAttachmentItem("red_dot", "+10% 精准射击"));
    public static final RegistryObject<Item> CD_SIGHT_EOTECH = ITEMS.register("cd_sight_eotech",
            () -> new SightAttachmentItem("eotech_holographic", "+15% 精准 快速瞄准"));
    public static final RegistryObject<Item> CD_SIGHT_ACOG = ITEMS.register("cd_sight_acog",
            () -> new SightAttachmentItem("acog", "4倍放大 +15% 远程精准"));
    public static final RegistryObject<Item> CD_SIGHT_8X = ITEMS.register("cd_sight_8x",
            () -> new SightAttachmentItem("8x_scope", "8倍放大 +25% 远程精准"));

    // ==================== 握把附件 ====================
    public static final RegistryObject<Item> CD_GRIP_VERTICAL = ITEMS.register("cd_grip_vertical",
            () -> new GripAttachmentItem("vertical_grip", Rarity.UNCOMMON, "-15% 后坐力"));
    public static final RegistryObject<Item> CD_GRIP_ANGLED = ITEMS.register("cd_grip_angled",
            () -> new GripAttachmentItem("angled_grip", Rarity.UNCOMMON, "-10% 后坐力 +5% 瞄准速度"));
    public static final RegistryObject<Item> CD_BIPOD = ITEMS.register("cd_bipod",
            () -> new GripAttachmentItem("bipod", Rarity.RARE, "-40% 后坐力（架设时）"));

    // ==================== 枪管附件 ====================
    public static final RegistryObject<Item> CD_BARREL_SUPPRESSOR = ITEMS.register("cd_barrel_suppressor",
            () -> new BarrelAttachmentItem("suppressor", Rarity.RARE, "-90% 噪音 隐藏开火"));
    public static final RegistryObject<Item> CD_BARREL_COMPENSATOR = ITEMS.register("cd_barrel_compensator",
            () -> new BarrelAttachmentItem("compensator", Rarity.UNCOMMON, "-20% 后坐力 -10% 散布"));
    public static final RegistryObject<Item> CD_BARREL_EXTENDED = ITEMS.register("cd_barrel_extended",
            () -> new BarrelAttachmentItem("extended_barrel", Rarity.RARE, "+15% 伤害 +20% 射程"));

    // ==================== 弹匣附件 ====================
    public static final RegistryObject<Item> CD_MAG_STANDARD = ITEMS.register("cd_mag_standard",
            () -> new MagazineAttachmentItem("standard_mag", Rarity.COMMON, "标准弹匣（备用）"));
    public static final RegistryObject<Item> CD_MAG_EXTENDED = ITEMS.register("cd_mag_extended",
            () -> new MagazineAttachmentItem("extended_mag", Rarity.RARE, "+50% 弹匣容量"));
    public static final RegistryObject<Item> CD_MAG_DRUM = ITEMS.register("cd_mag_drum",
            () -> new MagazineAttachmentItem("drum_mag", Rarity.EPIC, "+150% 弹匣容量 -20% 换弹速度"));

    // ==================== 近战武器 ====================
    public static final RegistryObject<Item> CD_COMBAT_KNIFE = ITEMS.register("cd_combat_knife", CombatKnifeItem::new);
    public static final RegistryObject<Item> CD_BOWIE_KNIFE = ITEMS.register("cd_bowie_knife", BowieKnifeItem::new);
    public static final RegistryObject<Item> CD_CROWBAR = ITEMS.register("cd_crowbar", CrowbarItem::new);

    // ==================== 投掷物（手雷） ====================
    public static final RegistryObject<Item> CD_FRAGMENT_GRENADE = ITEMS.register("cd_fragment_grenade", FragmentGrenadeItem::new);
    public static final RegistryObject<Item> CD_FLASHBANG = ITEMS.register("cd_flashbang", FlashbangGrenadeItem::new);
    public static final RegistryObject<Item> CD_MOLOTOV = ITEMS.register("cd_molotov", MolotovCocktailItem::new);

    // ==================== 防具/装备 ====================
    public static final RegistryObject<Item> CD_BALLISTIC_HELMET = ITEMS.register("cd_ballistic_helmet", BallisticHelmetItem::new);
    public static final RegistryObject<Item> CD_PLATE_CARRIER = ITEMS.register("cd_plate_carrier", PlateCarrierItem::new);
    public static final RegistryObject<Item> CD_TACTICAL_VEST = ITEMS.register("cd_tactical_vest", TacticalVestItem::new);
    public static final RegistryObject<Item> CD_COMBAT_BOOTS = ITEMS.register("cd_combat_boots", CombatBootsItem::new);
}
