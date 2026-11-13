package roguePlanets;

import com.fs.starfarer.api.campaign.CampaignEngineLayers;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.impl.campaign.BaseCustomEntityPlugin;
import com.fs.starfarer.api.impl.campaign.GenericCampaignEntitySprite;
import com.fs.starfarer.api.util.Misc;

/**
 * A tumbling chunk of crust orbiting a Sundered world. Drawn with one of the
 * graphics/planets/sundered_fragment*.png sprites, lit and shadowed by the system's star
 * the same way vanilla derelicts are.
 */
public class SunderedFragmentPlugin extends BaseCustomEntityPlugin {

    public static final String[] SPRITES = {
        "graphics/planets/sundered_fragment0.png",
        "graphics/planets/sundered_fragment1.png",
        "graphics/planets/sundered_fragment2.png",
        "graphics/planets/sundered_fragment3.png",
    };
    private static final float SPRITE_PIXELS = 256f;

    /** Passed to addCustomEntity as the plugin params. */
    public static class FragmentParams {
        public int spriteIndex;
        public float size;      // rendered width in campaign units
        public float spin;      // degrees per second, sign = direction
        public FragmentParams(int spriteIndex, float size, float spin) {
            this.spriteIndex = spriteIndex;
            this.size = size;
            this.spin = spin;
        }
    }

    private int spriteIndex;
    private float size = 40f;
    private float spin = 4f;
    private transient GenericCampaignEntitySprite sprite;

    @Override
    public void init(SectorEntityToken entity, Object params) {
        super.init(entity, params);
        if (params instanceof FragmentParams) {
            FragmentParams p = (FragmentParams) params;
            spriteIndex = Math.max(0, Math.min(SPRITES.length - 1, p.spriteIndex));
            size = p.size;
            spin = p.spin;
        }
        readResolve();
    }

    Object readResolve() {
        if (entity != null) {
            sprite = new GenericCampaignEntitySprite(entity, SPRITES[spriteIndex], size / SPRITE_PIXELS);
        }
        return this;
    }

    @Override
    public void advance(float amount) {
        if (entity != null && entity.isInCurrentLocation()) {
            entity.setFacing(Misc.normalizeAngle(entity.getFacing() + amount * spin));
        }
    }

    @Override
    public float getRenderRange() {
        return size + 100f;
    }

    @Override
    public void render(CampaignEngineLayers layer, ViewportAPI viewport) {
        float alphaMult = viewport.getAlphaMult();
        alphaMult *= entity.getSensorFaderBrightness();
        alphaMult *= entity.getSensorContactFaderBrightness();
        if (alphaMult <= 0) return;
        if (sprite == null) readResolve();
        sprite.render(0, 0, entity.getFacing(), alphaMult);
    }
}
