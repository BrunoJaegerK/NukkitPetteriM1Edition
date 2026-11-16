# Nukkit PetteriM1 Edition

This is a custom version of [Nukkit](https://github.com/CloudburstMC/Nukkit), a Minecraft Bedrock Edition server software written in Java.

See the [wiki](https://github.com/PetteriM1/NukkitPetteriM1Edition/wiki) for more details.

>[Download](https://github.com/PetteriM1/NukkitPetteriM1Edition/releases/latest)

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
