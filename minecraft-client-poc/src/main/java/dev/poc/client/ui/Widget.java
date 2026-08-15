package dev.poc.client.ui;

/** Minimal widget contract: layout rectangle, per-frame update, draw, and pointer input. */
public abstract class Widget {

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected boolean hovered;

    public void setBounds(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    /** Called once per frame with the frame delta, before {@link #render}. */
    public void update(float deltaSeconds, double mouseX, double mouseY) {
        hovered = contains(mouseX, mouseY);
    }

    public abstract void render(NanoVgRenderer gfx, Theme theme);

    /** @return true if the widget handled the click and it should not fall through. */
    public boolean onMouseButton(double mouseX, double mouseY, int button, boolean pressed) {
        return false;
    }
}
