// QLM Zombie Mod - Loot Table Injection Script
// Injects QLM items into dungeon chests, mineshaft chests, and modifies mob drops

// --- Chest Loot Table Injection ---
onEvent('loot_tables', event => {
    // Dungeon Chest (Simple Dungeon)
    event.modify('minecraft:chests/simple_dungeon', loot => {
        loot.addItem('qlmzombie:zombie_core', 10);
        loot.addItem('qlmzombie:infected_essence', 15);
        loot.addItem('qlmzombie:medical_supply', 8);
        loot.addItem('qlmzombie:reinforced_parts', 6);
        loot.addItem('qlmzombie:biohazard_sample', 2);
        loot.addItem('qlmzombie:tactical_ammo', 10);
        loot.addItem('qlmzombie:antidote', 5);
    });

    // Mineshaft Corridor Chest
    event.modify('minecraft:chests/abandoned_mineshaft', loot => {
        loot.addItem('qlmzombie:zombie_core', 12);
        loot.addItem('qlmzombie:infected_essence', 10);
        loot.addItem('qlmzombie:survival_kit', 8);
        loot.addItem('qlmzombie:reinforced_parts', 5);
        loot.addItem('qlmzombie:tactical_ammo', 8);
    });

    // Nether Fortress Chest
    event.modify('minecraft:chests/nether_bridge', loot => {
        loot.addItem('qlmzombie:biohazard_sample', 8);
        loot.addItem('qlmzombie:infected_essence', 10);
        loot.addItem('qlmzombie:reinforced_parts', 6);
        loot.addItem('qlmzombie:zombie_core', 5);
    });

    // Bastion Chest
    event.modify('minecraft:chests/bastion_treasure', loot => {
        loot.addItem('qlmzombie:biohazard_sample', 15);
        loot.addItem('qlmzombie:reinforced_parts', 10);
        loot.addItem('qlmzombie:zombie_core', 8);
        loot.addItem('qlmzombie:mode_switch', 2);
    });

    // Stronghold Chest
    event.modify('minecraft:chests/stronghold_corridor', loot => {
        loot.addItem('qlmzombie:zombie_core', 8);
        loot.addItem('qlmzombie:infected_essence', 12);
        loot.addItem('qlmzombie:medical_supply', 6);
    });

    // Village Plains House
    event.modify('minecraft:chests/village_plains_house', loot => {
        loot.addItem('qlmzombie:emergency_ration', 10);
        loot.addItem('qlmzombie:purified_water_bottle', 6);
        loot.addItem('qlmzombie:antidote', 3);
    });

    // Village Savanna House
    event.modify('minecraft:chests/village_savanna_house', loot => {
        loot.addItem('qlmzombie:emergency_ration', 10);
        loot.addItem('qlmzombie:purified_water_bottle', 6);
    });

    // Village Desert House
    event.modify('minecraft:chests/village_desert_house', loot => {
        loot.addItem('qlmzombie:emergency_ration', 12);
        loot.addItem('qlmzombie:purified_water_bottle', 8);
    });

    // Jungle Temple Dispenser
    event.modify('minecraft:chests/jungle_trap', loot => {
        loot.addItem('qlmzombie:tactical_ammo', 10);
        loot.addItem('qlmzombie:reinforced_parts', 5);
    });

    // Jungle Temple Chest
    event.modify('minecraft:chests/jungle_dispenser', loot => {
        loot.addItem('qlmzombie:zombie_core', 10);
        loot.addItem('qlmzombie:biohazard_sample', 5);
    });

    // End City Chest
    event.modify('minecraft:chests/end_city_treasure', loot => {
        loot.addItem('qlmzombie:biohazard_sample', 12);
        loot.addItem('qlmzombie:mode_switch', 5);
        loot.addItem('qlmzombie:reinforced_parts', 10);
    });

    // Underwater Ruin Chest
    event.modify('minecraft:chests/underwater_ruin_big', loot => {
        loot.addItem('qlmzombie:purified_water_bottle', 15);
        loot.addItem('qlmzombie:biohazard_sample', 6);
        loot.addItem('qlmzombie:zombie_core', 8);
    });

    // Igloo Chest
    event.modify('minecraft:chests/igloo_chest', loot => {
        loot.addItem('qlmzombie:medical_supply', 12);
        loot.addItem('qlmzombie:antidote', 8);
        loot.addItem('qlmzombie:infected_essence', 5);
    });

    // Pillager Outpost Chest
    event.modify('minecraft:chests/pillager_outpost', loot => {
        loot.addItem('qlmzombie:tactical_ammo', 12);
        loot.addItem('qlmzombie:reinforced_parts', 8);
        loot.addItem('qlmzombie:survival_kit', 5);
    });

    // Ancient City Chest
    event.modify('minecraft:chests/ancient_city', loot => {
        loot.addItem('qlmzombie:biohazard_sample', 20);
        loot.addItem('qlmzombie:mode_switch', 8);
        loot.addItem('qlmzombie:zombie_core', 15);
        loot.addItem('qlmzombie:infected_essence', 10);
    });
});

// --- Mob Drop Modifications ---
onEvent('entity.death', event => {
    const entity = event.entity;
    const level = entity.level;
    
    if (level.isClientSide) return;

    // Zombie drops
    if (entity.type === 'minecraft:zombie' || 
        entity.type === 'minecraft:zombie_villager' ||
        entity.type === 'minecraft:drowned' ||
        entity.type === 'minecraft:husk') {
        
        const random = level.random;
        
        // Zombie Core drop (10% chance)
        if (random.nextFloat() < 0.10) {
            entity.drop('qlmzombie:zombie_core', 1);
        }
        
        // Infected Essence drop (8% chance)
        if (random.nextFloat() < 0.08) {
            entity.drop('qlmzombie:infected_essence', 1);
        }
        
        // Biohazard Sample drop (2% chance)
        if (random.nextFloat() < 0.02) {
            entity.drop('qlmzombie:biohazard_sample', 1);
        }
        
        // Extra rotten flesh
        if (random.nextFloat() < 0.30) {
            entity.drop('minecraft:rotten_flesh', 1 + random.nextInt(2));
        }
    }

    // Cow drops - extra meat and hide
    if (entity.type === 'minecraft:cow') {
        const random = level.random;
        if (random.nextFloat() < 0.50) {
            entity.drop('minecraft:beef', 1);
        }
        if (random.nextFloat() < 0.25) {
            entity.drop('minecraft:leather', 1);
        }
        // Rare medical supply drop
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
        // Rare emergency ration
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
        // Rare purified water
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
onEvent('block.break', event => {
    const block = event.block;
    const level = event.level;
    if (level.isClientSide) return;

    // When a chest is broken, sometimes drop QLM items
    if (block === 'minecraft:chest' || 
        block === 'minecraft:barrel' ||
        block === 'minecraft:trapped_chest') {
        
        const random = level.random;
        
        // Small chance to drop survival kit from broken chest
        if (random.nextFloat() < 0.02) {
            event.dropItem('qlmzombie:survival_kit');
        }
        if (random.nextFloat() < 0.015) {
            event.dropItem('qlmzombie:medical_supply');
        }
    }
});

// --- Fishing Loot Injection ---
onEvent('loot_tables', event => {
    // Fishing treasure
    event.modify('minecraft:gameplay/fishing/treasure', loot => {
        loot.addItem('qlmzombie:purified_water_bottle', 5);
        loot.addItem('qlmzombie:antidote', 2);
    });

    // Fishing junk
    event.modify('minecraft:gameplay/fishing/junk', loot => {
        loot.addItem('qlmzombie:infected_essence', 3);
    });
});

// --- Barter Injection (Piglin bartering) ---
onEvent('loot_tables', event => {
    event.modify('minecraft:gameplay/piglin_barter', loot => {
        loot.addItem('qlmzombie:biohazard_sample', 3);
        loot.addItem('qlmzombie:mode_switch', 1);
    });
});