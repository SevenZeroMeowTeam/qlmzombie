// QLM Zombie Mod - Loot Table Injection Script
// Injects QLM items into dungeon chests, mineshaft chests, and modifies mob drops

// --- Chest Loot Table Injection (all chest types) ---
LootJS.modifiers(event => {
    event.addLootTypeModifier('chest').addLoot(
        'qlmzombie:zombie_core', 'qlmzombie:infected_essence', 'qlmzombie:medical_supply',
        'qlmzombie:reinforced_parts', 'qlmzombie:biohazard_sample', 'qlmzombie:tactical_ammo',
        'qlmzombie:antidote', 'qlmzombie:survival_kit', 'qlmzombie:emergency_ration',
        'qlmzombie:purified_water_bottle', 'qlmzombie:mode_switch'
    );

    // --- Fishing Loot Injection ---
    event.addLootTypeModifier('fishing').addLoot(
        'qlmzombie:purified_water_bottle', 'qlmzombie:antidote', 'qlmzombie:infected_essence'
    );

    // --- Barter Injection (Piglin bartering) ---
    event.addLootTypeModifier('piglin_barter').addLoot(
        'qlmzombie:biohazard_sample', 'qlmzombie:mode_switch'
    );
});

// --- Mob Drop Modifications ---
EntityEvents.death(event => {
    const entity = event.entity;
    const level = entity.level;

    if (level.isClientSide) return;

    // Zombie drops
    if (entity.type === 'minecraft:zombie' ||
        entity.type === 'minecraft:zombie_villager' ||
        entity.type === 'minecraft:drowned' ||
        entity.type === 'minecraft:husk') {

        const random = level.random;

        if (random.nextFloat() < 0.10) {
            entity.drop('qlmzombie:zombie_core', 1);
        }
        if (random.nextFloat() < 0.08) {
            entity.drop('qlmzombie:infected_essence', 1);
        }
        if (random.nextFloat() < 0.02) {
            entity.drop('qlmzombie:biohazard_sample', 1);
        }
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:rotten_flesh', 1 + random.nextInt(2));
        }
    }

    // Cow drops
    if (entity.type === 'minecraft:cow') {
        const random = level.random;
        if (random.nextFloat() < 0.50) {
            entity.drop('minecraft:beef', 1);
        }
        if (random.nextFloat() < 0.25) {
            entity.drop('minecraft:leather', 1);
        }
        if (random.nextFloat() < 0.03) {
            entity.drop('qlmzombie:medical_supply', 1);
        }
    }

    // Pig drops
    if (entity.type === 'minecraft:pig') {
        const random = level.random;
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:porkchop', 1);
        }
        if (random.nextFloat() < 0.02) {
            entity.drop('qlmzombie:emergency_ration', 1);
        }
    }

    // Sheep drops
    if (entity.type === 'minecraft:sheep') {
        const random = level.random;
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:mutton', 1);
        }
        if (random.nextFloat() < 0.02) {
            entity.drop('qlmzombie:purified_water_bottle', 1);
        }
    }

    // Chicken drops
    if (entity.type === 'minecraft:chicken') {
        const random = level.random;
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:chicken', 1);
        }
    }

    // Rabbit drops
    if (entity.type === 'minecraft:rabbit') {
        const random = level.random;
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:rabbit', 1);
        }
    }

    // Fox drops
    if (entity.type === 'minecraft:fox') {
        const random = level.random;
        if (random.nextFloat() < 0.10) {
            entity.drop('minecraft:cod', 1);
        }
    }

    // Creeper drops - tactical ammo
    if (entity.type === 'minecraft:creeper') {
        const random = level.random;
        if (random.nextFloat() < 0.15) {
            entity.drop('qlmzombie:tactical_ammo', 1);
        }
    }

    // Skeleton drops - tactical ammo
    if (entity.type === 'minecraft:skeleton' || entity.type === 'minecraft:stray') {
        const random = level.random;
        if (random.nextFloat() < 0.08) {
            entity.drop('qlmzombie:tactical_ammo', 1);
        }
    }

    // Spider drops - infected essence
    if (entity.type === 'minecraft:spider' || entity.type === 'minecraft:cave_spider') {
        const random = level.random;
        if (random.nextFloat() < 0.06) {
            entity.drop('qlmzombie:infected_essence', 1);
        }
    }

    // Enderman drops - mode switch
    if (entity.type === 'minecraft:enderman') {
        const random = level.random;
        if (random.nextFloat() < 0.01) {
            entity.drop('qlmzombie:mode_switch', 1);
        }
    }

    // Wither Skeleton drops - biohazard sample
    if (entity.type === 'minecraft:wither_skeleton') {
        const random = level.random;
        if (random.nextFloat() < 0.04) {
            entity.drop('qlmzombie:biohazard_sample', 1);
        }
    }
});

// --- Block Loot Modifications (Chest Break Drops) ---
BlockEvents.broken(event => {
    const block = event.block;
    const level = event.level;
    if (level.isClientSide) return;

    if (block === 'minecraft:chest' ||
        block === 'minecraft:barrel' ||
        block === 'minecraft:trapped_chest') {

        const random = level.random;

        if (random.nextFloat() < 0.02) {
            event.dropItem('qlmzombie:survival_kit');
        }
        if (random.nextFloat() < 0.015) {
            event.dropItem('qlmzombie:medical_supply');
        }
    }
});
