package roguePlanets.story;

import java.awt.Color;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import org.lwjgl.opengl.GL11;

/**
 * A grid of portraits drawn inside the dialogue. The mouse wheel scrolls it and a click chooses a face (it gets a gold frame).
 * Used when the player picks a face for Ellis. Only the faces on screen are loaded.
 */
public class FacePicker implements CustomUIPanelPlugin {

    public static final float W = 580f, H = 330f;
    public static final int COLS = 6;
    private static final float CELL = 84f, GAP = 10f;
    private static final int VISIBLE_ROWS = 3;
    public static final float CAPTION_H = 30f; // the strip under the grid, for the instructions

    private final List<String> faces;
    private int selected;
    private int scrollRow;
    private PositionAPI pos;
    private int hover = -1;
    private int mouseX = -9999, mouseY = -9999;
    private final Map<String, SpriteAPI> cache = new HashMap<String, SpriteAPI>();

    public FacePicker(List<String> faces, int selected, int scrollRow) {
        this.faces = faces;
        this.selected = selected;
        this.scrollRow = scrollRow;
        clamp();
    }

    public int getSelected() { return selected; }
    public int getScrollRow() { return scrollRow; }

    private int totalRows() { return (faces.size() + COLS - 1) / COLS; }
    private int maxRow() { return Math.max(0, totalRows() - VISIBLE_ROWS); }
    private void clamp() { scrollRow = Math.max(0, Math.min(maxRow(), scrollRow)); }

    private float left() { return pos.getX() + (W - (COLS * CELL + (COLS - 1) * GAP)) / 2f; }
    private float top() { return pos.getY() + pos.getHeight() - GAP; }
    private float cellX(int c) { return left() + c * (CELL + GAP); }
    private float cellY(int r) { return top() - (r + 1) * CELL - r * GAP; }

    private int hit(int mx, int my) {
        if (pos == null) return -1;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= faces.size()) continue;
                float x = cellX(c), y = cellY(r);
                if (mx >= x && mx <= x + CELL && my >= y && my <= y + CELL) return idx;
            }
        }
        return -1;
    }

    private SpriteAPI sprite(String path) {
        SpriteAPI s = cache.get(path);
        if (s == null) {
            try { Global.getSettings().loadTexture(path); } catch (Throwable t) { /* already loaded, or missing */ }
            try { s = Global.getSettings().getSprite(path); } catch (Throwable t) { s = null; }
            if (s != null) cache.put(path, s);
        }
        return s;
    }

    /** A flat coloured rectangle, drawn with textures off so a portrait's texture never bleeds into it. */
    private static void quad(float x, float y, float w, float h, Color c, float alpha) {
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, alpha);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glVertex2f(x, y);
        GL11.glVertex2f(x + w, y);
        GL11.glVertex2f(x + w, y + h);
        GL11.glVertex2f(x, y + h);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }

    private void frame(float x, float y, float size, float t, Color c, float alpha) {
        quad(x - t, y - t, size + 2 * t, t, c, alpha);          // bottom
        quad(x - t, y + size, size + 2 * t, t, c, alpha);       // top
        quad(x - t, y, t, size, c, alpha);                      // left
        quad(x + size, y, t, size, c, alpha);                   // right
    }

    @Override public void positionChanged(PositionAPI position) { this.pos = position; }

    @Override public void renderBelow(float alphaMult) {
        if (pos == null) return;
        quad(pos.getX(), pos.getY(), pos.getWidth(), pos.getHeight(), new Color(8, 8, 18), 0.75f * alphaMult);
    }

    @Override public void render(float alphaMult) {
        if (pos == null) return;
        for (int r = 0; r < VISIBLE_ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int idx = (scrollRow + r) * COLS + c;
                if (idx >= faces.size()) continue;
                float x = cellX(c), y = cellY(r);
                SpriteAPI s = sprite(faces.get(idx));
                if (s != null) {
                    s.setSize(CELL, CELL);
                    s.setColor(Color.WHITE);
                    s.setAlphaMult(alphaMult);
                    s.render(x, y);
                } else {
                    quad(x, y, CELL, CELL, new Color(40, 40, 50), alphaMult);
                }
                if (idx == selected) frame(x, y, CELL, 3f, new Color(255, 215, 120), alphaMult);
                else if (idx == hover) frame(x, y, CELL, 2f, new Color(190, 170, 255), alphaMult);
            }
        }
        // a thin scroll bar down the right edge
        int total = totalRows();
        if (total > VISIBLE_ROWS) {
            float trackH = pos.getHeight() - CAPTION_H - 2 * GAP;
            float barH = Math.max(18f, trackH * VISIBLE_ROWS / total);
            float frac = maxRow() == 0 ? 0f : (float) scrollRow / maxRow();
            float y = pos.getY() + CAPTION_H + GAP + (trackH - barH) * (1f - frac);
            quad(pos.getX() + pos.getWidth() - 6f, y, 3f, barH, new Color(170, 150, 230), 0.8f * alphaMult);
        }
    }

    @Override public void advance(float amount) { }

    @Override public void processInput(List<InputEventAPI> events) {
        if (pos == null) return;
        for (InputEventAPI e : events) {
            if (e.isConsumed()) continue;
            if (e.isMouseEvent()) { mouseX = e.getX(); mouseY = e.getY(); hover = hit(mouseX, mouseY); }
            if (e.isMouseScrollEvent() && pos.containsEvent(e)) {
                scrollRow += e.getEventValue() > 0 ? -1 : 1;
                clamp();
                e.consume();
            } else if (e.isLMBDownEvent()) {
                int idx = hit(e.getX(), e.getY());
                if (idx >= 0) { selected = idx; e.consume(); }
            }
        }
    }

    @Override public void buttonPressed(Object buttonId) { }
}
