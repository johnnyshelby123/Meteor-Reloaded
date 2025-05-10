package meteordevelopment.meteorclient.systems.modules.misc;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class SaveDeath extends Module {
    private boolean justDiedProcessed = false;
    private float previousHealth = -1f;

    public SaveDeath() {
        super(Categories.Misc, "save-death", "Saves your coordinates to your clipboard when you die. Works with auto-respawn.");
    }

    @Override
    public void onActivate() {
        justDiedProcessed = false;
        if (mc.player != null) {
            previousHealth = mc.player.getHealth();
        } else {
            previousHealth = -1f; // Reset if player is null
        }
    }

    @Override
    public void onDeactivate() {
        justDiedProcessed = false;
        previousHealth = -1f;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) {
            // If player becomes null (e.g. leaving server), reset previousHealth
            if (previousHealth != -1f) previousHealth = -1f;
            return;
        }

        float currentHealth = mc.player.getHealth();

        // Initialize previousHealth on the first tick if not already set
        if (previousHealth == -1f && mc.player.isAlive()) {
            previousHealth = currentHealth;
        }

        // Death condition: Health was positive and now it is zero or less
        if (previousHealth > 0 && currentHealth <= 0) {
            if (!justDiedProcessed) {
                // Player has just died based on health transition
                double x = mc.player.getX();
                double y = mc.player.getY();
                double z = mc.player.getZ();
                String dimension = mc.world.getRegistryKey().getValue().toString();

                String deathCoords = String.format("Death Coords: X: %.2f, Y: %.2f, Z: %.2f (Dimension: %s)", x, y, z, dimension);
                mc.keyboard.setClipboard(deathCoords);
                info(Text.literal("Death coordinates copied to clipboard: ").append(Text.literal(deathCoords).formatted(Formatting.YELLOW)));
                
                justDiedProcessed = true; 
            }
        } else if (currentHealth > 0) {
            // Player is alive, so reset the justDiedProcessed flag if they were previously marked as processed.
            // This allows the module to trigger again if the player dies after respawning.
            if (justDiedProcessed) {
                justDiedProcessed = false;
            }
        }
        
        // Update previousHealth for the next tick
        previousHealth = currentHealth;
    }
}

