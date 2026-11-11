package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.HashMap;

public class EntityCreaking extends EntityWalkingMob {

    public static final int NETWORK_ID = 146;

    public EntityCreaking(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(1);
        super.initEntity();
        this.setDamage(new int[]{0, 2, 3, 4});
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.9f;
    }

    @Override
    public float getHeight() {
        return 2.7f;
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    public void attackEntity(Entity player) {
        if (!(player instanceof Player) || this.attackDelay <= 30
                || player.distanceSquared(this) > 2 + this.getWidth() * this.getWidth()) {
            return;
        }

        this.attackDelay = 0;
        HashMap<EntityDamageEvent.DamageModifier, Float> damage = new HashMap<>();
        damage.put(EntityDamageEvent.DamageModifier.BASE, (float) this.getDamage());

        float armorPoints = 0;
        for (Item armor : ((Player) player).getInventory().getArmorContents()) {
            armorPoints += this.getArmorPoints(armor.getId());
        }
        damage.put(EntityDamageEvent.DamageModifier.ARMOR,
                (float) (damage.getOrDefault(EntityDamageEvent.DamageModifier.ARMOR, 0f)
                        - Math.floor(damage.getOrDefault(EntityDamageEvent.DamageModifier.BASE, 1f) * armorPoints * 0.04)));

        player.attack(new EntityDamageByEntityEvent(this, player, EntityDamageEvent.DamageCause.ENTITY_ATTACK, damage));
        this.playAttack();
    }
}

