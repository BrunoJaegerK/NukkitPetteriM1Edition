# Nukkit PetteriM1 Edition

This is a custom version of [Nukkit](https://github.com/CloudburstMC/Nukkit), a Minecraft Bedrock Edition server software written in Java.

See the [wiki](https://github.com/PetteriM1/NukkitPetteriM1Edition/wiki) for more details.

>[Download](https://github.com/BrunoJaegerK/NukkitPetteriM1Edition/releases/latest)

## Enhanced vanilla mob AI

This fork includes dedicated AI improvements for the following Bedrock entities:

- **Ender Dragon:** target acquisition, orbiting and strafing, close charge passes, ranged fireball attacks, crystal healing, and non-colliding flight movement.
- **Enderman:** player-stare anger, pursuit teleports, water/rain/fire escape, Endermite targeting, and vanilla-style block pickup and placement.
- **Shulker:** attachment and peek metadata, defensive shell behavior, projectile attacks with levitation, safe relocation, and retaliation against attackers.
- **Villagers:** daily work, meeting and rest routines, hostile-mob avoidance, baby following, food pickup/sharing, wheat-to-bread crafting, breeding, sleeping state, rain shelter, wooden-door opening/closing, and farmer crop harvesting/replanting.
- **Villager professions:** unemployed villagers can claim nearby workstations (composter, barrel, loom, fletching table, lectern, cartography table, brewing stand, blast furnace, grindstone, smithing table, smoker, cauldron and stonecutter). Their Bedrock profession/skin metadata updates immediately; villagers without trades can lose the profession if their claimed workstation is removed.
- **Villager infection:** zombies and husks only convert villagers they kill, using the vanilla difficulty chances (0% Easy, 50% Normal, 100% Hard), and preserve the villager's NBT at the same location.

### Required settings

- Enable the server's `mob-ai` setting so entity AI can run.
- Keep the `mobGriefing` game rule enabled if you want Endermen to move blocks and farmer villagers to harvest and plant crops.

### New cancellable events

Plugins can listen for these events to control the new block interactions:

- `EndermanBlockPlaceEvent`
- `VillagerHarvestEvent`
- `VillagerPlantEvent`
- `VillagerDoorOpenEvent`

## Vanilla natural mob spawning

The natural spawning pipeline has been refined to behave more consistently with Bedrock's player-centered spawning rules:

- Expand the natural spawn search to the vanilla player radius, so eligible spawn locations are searched across the intended area around players rather than a reduced radius.
- Improve hostile-mob classification so hostile entities that were previously missing from the classification are recognized by natural spawning and sleep-related hostile-mob checks.
- Keep natural spawning classification and sleep checks aligned, avoiding cases where a hostile mob is treated differently by those systems.

These changes concern natural mob spawning and hostile-mob classification. They do not mean that every mob's biome, light, structure, pack-size, dimension, or spawn-condition table has been fully reimplemented from vanilla.

## Ender Dragon behavior

- Improve target acquisition and refresh combat flight phases when the target changes.
- Add circling and strafing flight phases around players, with close-range charge passes.
- Tune flight speed, collision behavior, and hitbox dimensions to better match Bedrock movement.
- Improve dragon armor/damage calculations and preserve synchronization when mob AI is disabled.

## Enderman behavior

- Match Bedrock gaze detection range and the short gaze trigger timing; require line of sight and valid player targets.
- Improve anger duration, timing, and persistence, including pursuit teleports.
- Retry teleports at safe nearby positions and require sufficient clearance before teleporting.
- Escape fire, water, and rain; target nearby Endermites.
- Pick up and place supported vanilla blocks, including dandelions, with a cancellable placement event.

## Shulker behavior

- Add attachment-face and peek-state metadata, with recovery and relocation to safe positions on supported faces.
- Improve shell hitbox behavior, armor, projectile deflection, and retaliation target selection.
- Improve Shulker bullet behavior, including Bedrock-style speed, difficulty-scaled damage, and levitation behavior that does not affect Shulkers themselves.
- Respect peaceful difficulty and player game modes when handling movement and retaliation.

## Villager behavior and persistence

- Improve daily work, meeting, and rest routines, including pathfinding to points of interest and reserving beds/workstations.
- Improve workstation claiming, profession metadata/skin updates, and release of job sites or professions when the workstation is no longer available.
- Improve farmer inventory accounting, crop harvesting/replanting, and safe planting checks.
- Support food pickup, sharing surplus food, wheat-to-bread crafting, and food-based breeding; consume stored food across breeding cycles and preserve excess stock.
- Persist farm stock and trade state, clamp trade tiers, and handle world-height edge cases.
- Improve hostile-threat avoidance, panic behavior, rain shelter, door navigation, and baby villager following.

## Zombie and husk interactions

- Apply villager infection only when a zombie or husk deals the fatal hit.
- Use the vanilla difficulty-based infection chances: 0% on Easy, 50% on Normal, and 100% on Hard.
- Preserve villager NBT and spawn the converted villager at the death location.

## Plugin events

The following cancellable events are available for integrations that need to control new mob block interactions:

- `EndermanBlockPlaceEvent`: called before an Enderman places a carried block.
- `VillagerHarvestEvent`: called before a farmer villager harvests crops.
- `VillagerPlantEvent`: called before a farmer villager plants crops.
- `VillagerDoorOpenEvent`: called before a villager opens a wooden door.

## Notes

- These are targeted vanilla-behavior improvements, not a claim that every Bedrock mob mechanic or every spawn table is now identical to the official game.
- Natural spawning still depends on the server's existing world, difficulty, game-rule, chunk-ticking, and mob-AI settings.
