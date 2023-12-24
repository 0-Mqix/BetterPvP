package me.mykindos.betterpvp.champions.champions.skills.skills.brute.axe;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.mykindos.betterpvp.champions.Champions;
import me.mykindos.betterpvp.champions.champions.ChampionsManager;
import me.mykindos.betterpvp.champions.champions.skills.Skill;
import me.mykindos.betterpvp.champions.champions.skills.data.SkillActions;
import me.mykindos.betterpvp.champions.champions.skills.types.CooldownSkill;
import me.mykindos.betterpvp.champions.champions.skills.types.InteractSkill;
import me.mykindos.betterpvp.core.combat.events.CustomDamageEvent;
import me.mykindos.betterpvp.core.components.champions.Role;
import me.mykindos.betterpvp.core.components.champions.SkillType;
import me.mykindos.betterpvp.core.framework.updater.UpdateEvent;
import me.mykindos.betterpvp.core.listener.BPvPListener;
import me.mykindos.betterpvp.core.utilities.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.WeakHashMap;

@Singleton
@BPvPListener
public class Takedown extends Skill implements InteractSkill, CooldownSkill, Listener {

    private final WeakHashMap<Player, Long> active = new WeakHashMap<>();

    private double damage;

    private double baseDuration;

    private double durationIncreasePerLevel;

    private int slownessStrength;

    @Inject
    public Takedown(Champions champions, ChampionsManager championsManager) {
        super(champions, championsManager);
    }

    @Override
    public String getName() {
        return "Takedown";
    }

    @Override
    public String[] getDescription(int level) {

        return new String[]{
                "Right click with an Axe to activate",
                "",
                "Hurl yourself forwards",
                "",
                "If you collide with an enemy, you both",
                "take <stat>" + damage + "</stat> damage and receive <effect>Slowness " + UtilFormat.getRomanNumeral(slownessStrength + 1) + "</effect>",
                "for <val>" + getDuration(level) + "</val> seconds",
                "",
                "Cannot be used while grounded",
                "",
                "Cooldown: <val>" + getCooldown(level)
        };
    }

    public double getDuration(int level) {
        return baseDuration + level * durationIncreasePerLevel;
    }

    @Override
    public Role getClassType() {
        return Role.BRUTE;
    }

    @Override
    public SkillType getType() {
        return SkillType.AXE;
    }

    @Override
    public double getCooldown(int level) {

        return cooldown - ((level - 1) * 2);
    }


    @UpdateEvent
    public void checkCollision() {

        Iterator<Entry<Player, Long>> it = active.entrySet().iterator();
        while (it.hasNext()) {

            Entry<Player, Long> next = it.next();
            Player player = next.getKey();
            if (player.isDead()) {
                it.remove();
                continue;
            }

            if(isCollision(player)) {
                it.remove();
                continue;
            }


            if (UtilBlock.isGrounded(player)) {
                if (!player.hasPotionEffect(PotionEffectType.SLOW)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, 50, slownessStrength));
                }
                if (UtilTime.elapsed(next.getValue(), 750L)) {
                    it.remove();
                }
            }
        }

    }

    public boolean isCollision(Player player) {
        for (Player other : UtilPlayer.getNearbyEnemies(player, player.getLocation(), 1.5)) {
            if (other.isDead()) continue;

            if (UtilMath.offset(player, other) < 1.5) {

                doTakedown(player, other);
                return true;

            }
        }

        return false;
    }


    public void doTakedown(Player player, Player target) {
        UtilMessage.simpleMessage(player, getClassType().getName(), "You hit <alt>" + target.getName() + "</alt> with <alt>" + getName());

        UtilDamage.doCustomDamage(new CustomDamageEvent(target, player, null, DamageCause.CUSTOM, damage, false, "Takedown"));


        UtilMessage.simpleMessage(target, getClassType().getName(), "<alt>" + player.getName() + "</alt> hit you with <alt>" + getName());
        UtilDamage.doCustomDamage(new CustomDamageEvent(player, target, null, DamageCause.CUSTOM, damage, false, "Takedown Recoil"));

        PotionEffect pot = new PotionEffect(PotionEffectType.SLOW, (int) (getDuration(getLevel(player))) * 20, slownessStrength);
        player.addPotionEffect(pot);
        target.addPotionEffect(pot);
    }

    @Override
    public boolean canUse(Player p) {

        if (UtilBlock.isGrounded(p)) {
            UtilMessage.simpleMessage(p, getClassType().getName(), "You cannot use <alt>" + getName() + "</alt> while grounded.");
            return false;
        }

        return true;
    }

    @Override
    public void activate(Player player, int leel) {
        Vector vec = player.getLocation().getDirection();
        UtilVelocity.velocity(player, vec, 1.8D, false, 0.0D, 0.4D, 0.6D, false);
        active.put(player, System.currentTimeMillis());
    }


    @Override
    public Action[] getActions() {
        return SkillActions.RIGHT_CLICK;
    }

    @Override
    public void loadSkillConfig(){
        damage = getConfig("damage", 10.0, Double.class);
        baseDuration = getConfig("baseDuration", 1.0, Double.class);
        durationIncreasePerLevel = getConfig("durationIncreasePerLevel", 1.0, Double.class);
        slownessStrength = getConfig("slownessStrength", 3, Integer.class);
    }
}