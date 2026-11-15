package gregtech.api.mui.fake;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.screen.GuiContainerWrapper;
import com.cleanroommc.modularui.screen.ModularContainer;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a {@link ModularScreen} onto a block face instead of onto the player's screen, and feeds it the synthetic
 * mouse the player aims at that face with. The MUI2 replacement for the old {@code FakeModularGui}.
 * <p>
 * A {@link ModularScreen} does not actually require the game to display it: it only needs an {@link
 * com.cleanroommc.modularui.api.IMuiScreen IMuiScreen} wrapper and someone to drive its lifecycle. This class is that
 * someone - it owns a {@link GuiScreen} that is never shown and calls resize / update / draw itself.
 * <p>
 * Panels rendered this way come in two flavours:
 * <ul>
 * <li>client only, built without a container of its own. Sync handlers do not work, so everything the panel shows
 * must be readable from data the client already has, and every edit must be sent to the server by the caller.</li>
 * <li>synced, built on top of a {@link ModularContainer} whose {@link
 * com.cleanroommc.modularui.value.sync.ModularSyncManager ModularSyncManager} was registered with {@link
 * com.cleanroommc.modularui.network.ModularNetwork#CLIENT ModularNetwork.CLIENT}. Sync handlers then work as they do
 * in a normal gui, even though the container is not the player's open container.</li>
 * </ul>
 */
@SideOnly(Side.CLIENT)
public class FakeModularScreen {

    /**
     * Area the first layout pass gets. A panel sizes itself from its own flex, so this only has to be big enough not
     * to constrain it - in-world panels are never relative to the game window.
     */
    private static final int LAYOUT_SPACE = 1024;
    /** The name {@link com.cleanroommc.modularui.widgets.SlotGroupWidget SlotGroupWidget} gives the player slots. */
    private static final String PLAYER_INVENTORY = "player_inventory";
    /** How much the gui's own z axis is squashed by, so a hundred gui units stay well inside one block. */
    private static final float Z_FLATTENING = 0.00001f;
    /** The z {@link ModularScreen} draws foreground layers at. */
    private static final int FOREGROUND_Z = 100;

    private final ModularScreen screen;
    /** Never displayed. Exists because {@link ModularScreen} wants an {@code IMuiScreen} to talk to. */
    private final GuiScreen wrapper;

    private int width;
    private int height;

    public FakeModularScreen(@NotNull ModularScreen screen, @NotNull UISettings settings) {
        this(screen, settings, null, false);
    }

    public FakeModularScreen(@NotNull ModularScreen screen, @NotNull UISettings settings,
                             @Nullable ModularContainer container) {
        this(screen, settings, container, false);
    }

    /**
     * @param hidePlayerInventory drop the player inventory from the panel. A gui drawn on a block face is read from a
     *                            distance and its slots cannot be clicked anyway, so the viewer's own inventory is
     *                            only in the way. Client side only, which is fine: the sync handlers were collected
     *                            before this and are untouched, so both sides still agree on every key.
     */
    public FakeModularScreen(@NotNull ModularScreen screen, @NotNull UISettings settings,
                             @Nullable ModularContainer container, boolean hidePlayerInventory) {
        this.screen = screen;
        screen.getContext().setSettings(settings);
        this.wrapper = new GuiContainerWrapper(container != null ? container : clientOnlyContainer(), screen);
        initWrapper();
        layout(hidePlayerInventory);
        this.wrapper.width = this.width;
        this.wrapper.height = this.height;
    }

    /**
     * A container carrying no sync manager, which is what marks the screen client only. The panel still needs one:
     * {@link com.cleanroommc.modularui.widgets.slot.ItemSlot ItemSlot} draws itself off the fields only a
     * {@link GuiContainer} has, so a panel with slots in it - the layout the recipe viewer borrows from a recipe map
     * has plenty - throws on its first frame behind a plain {@link com.cleanroommc.modularui.screen.GuiScreenWrapper
     * GuiScreenWrapper}. This is how MUI2 itself backs a client only gui, in {@code GuiManager#openScreen}.
     */
    private static ModularContainer clientOnlyContainer() {
        ModularContainer container = new ModularContainer();
        container.constructClientOnly();
        return container;
    }

    /**
     * Opens the panel and shrinks the screen around it, so that the panel ends up at 0, 0 and the widget tree can be
     * drawn straight into the block face without another offset.
     */
    private void layout(boolean hidePlayerInventory) {
        this.screen.onResize(LAYOUT_SPACE, LAYOUT_SPACE);
        ModularPanel panel = this.screen.getMainPanel();
        if (hidePlayerInventory) {
            IWidget inventory = WidgetTree.findFirstWithNameNullable(panel, PLAYER_INVENTORY);
            if (inventory != null) {
                // shrink by exactly what the inventory took up; whatever else is anchored to the bottom of the
                // panel, such as a row of buttons beside it, then flows back up into the freed space
                int freed = inventory.getArea().height;
                inventory.setEnabled(false);
                panel.height(Math.max(panel.getArea().height - freed, 1));
                this.screen.onResize(LAYOUT_SPACE, LAYOUT_SPACE);
            }
        }
        Area area = panel.getArea();
        this.width = area.width;
        this.height = area.height;
        this.screen.onResize(this.width, this.height);
    }

    public ModularScreen getScreen() {
        return this.screen;
    }

    public GuiScreen getWrapper() {
        return this.wrapper;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /** Call once per client tick, as long as this screen is being rendered. */
    public void updateScreen() {
        this.screen.onUpdate();
        this.screen.onFrameUpdate();
    }

    public void dispose() {
        this.screen.getPanelManager().closeAll();
        this.screen.getPanelManager().dispose();
    }

    /**
     * Draws the panel onto the block face.
     * <p>
     * This deliberately does not call {@link ModularScreen#drawScreen()}: that one is written for a gui owning the
     * whole window, so it turns the depth test off and clears the depth buffer between panels. In the middle of a
     * world render the clear is destructive - it wipes the depth of the entire frame, which lets water and blocks
     * draw through each other and lets anything drawn after the block paint over the panel. Driving the panel loop
     * here keeps the depth test on and never clears.
     *
     * @param x            where the player is aiming on the face, 0 ~ 1 from the left
     * @param y            where the player is aiming on the face, 0 ~ 1 from the top
     * @param partialTicks render partial ticks
     */
    public void drawScreen(double x, double y, float partialTicks) {
        if (this.width <= 0 || this.height <= 0) return;

        float halfW = this.width / 2f;
        float halfH = this.height / 2f;
        float scale = scale();

        GlStateManager.pushMatrix();
        GlStateManager.translate(-scale * halfW, -scale * halfH, 0);
        // z is left unscaled here, so the lift below is in block units, the way the legacy fake gui had it
        GlStateManager.scale(scale, scale, 1);

        GlStateManager.disableLighting();
        RenderHelper.disableStandardItemLighting();
        GlStateManager.enableBlend();
        // gui drawables expect the alpha test off, the world render leaves it on for the cutout pass
        GlStateManager.disableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        // the world still has to be able to hide the panel
        GlStateManager.enableDepth();
        // The panel sits a hair in front of the face it is drawn on, close enough for the two to fight over depth.
        // Bias it towards the viewer, the way vanilla keeps the block breaking overlay off the block.
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-3f, -3f);

        // The legacy fake gui drew the gui background with depth writes still on, which is what gave the page a depth
        // of its own - without one the widgets have nothing to pass the depth test against and the block wins. MUI2
        // widgets bring their own background, so stamp that depth with a colourless quad instead.
        GlStateManager.depthMask(true);
        GlStateManager.colorMask(false, false, false, false);
        GuiDraw.drawRect(0, 0, this.width, this.height, -1);
        GlStateManager.colorMask(true, true, true, true);

        // Lift the widgets clear of that stamp, again as the legacy gui did. Without the gap they sit exactly in its
        // plane and fight it for every pixel, which is what made the page shimmer. Their own layering is flattened
        // on top of that: MUI2 puts foreground layers a hundred gui units up, which out here would be blocks.
        GlStateManager.translate(0, 0, 0.001);
        GlStateManager.scale(1, 1, Z_FLATTENING);
        GlStateManager.depthMask(false);

        ModularGuiContext context = this.screen.getContext();
        context.updateState(toMouseX(x), toMouseY(y), partialTicks);
        drawPanels(context);

        // leave the state the world render expects to find
        GlStateManager.doPolygonOffset(0f, 0f);
        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
        GlStateManager.popMatrix();
    }

    /**
     * Draws the panel as a flat gui, at the caller's current origin. Used where the panel is already inside a gui
     * render - the recipe viewer draws a recipe map's layout this way - so none of the world-render fixups
     * {@link #drawScreen} needs apply: the caller has already set up an orthographic gui projection, and clearing or
     * writing depth here would be as destructive as it is in the world.
     * <p>
     * Only the alpha test is touched, which MUI2 drawables want off. Everything else is left exactly as the caller
     * had it and put back afterwards: the recipe viewer draws the recipe's ingredients itself once this returns, so
     * lighting set up to suit MUI2 would land on those instead.
     *
     * @param mouseX       mouse x, relative to the panel's top left corner
     * @param mouseY       mouse y, relative to the panel's top left corner
     * @param partialTicks render partial ticks
     */
    public void drawInGui(int mouseX, int mouseY, float partialTicks) {
        if (this.width <= 0 || this.height <= 0) return;

        ModularGuiContext context = this.screen.getContext();
        context.updateState(mouseX, mouseY, partialTicks);

        GlStateManager.pushMatrix();
        GlStateManager.disableLighting();
        GlStateManager.disableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.color(1f, 1f, 1f, 1f);

        drawPanels(context);

        GlStateManager.popMatrix();
        // exactly the state the recipe viewer sets up before it hands over
        GlStateManager.disableLighting();
        GlStateManager.enableAlpha();
        GlStateManager.color(1f, 1f, 1f, 1f);
    }

    private void drawPanels(ModularGuiContext context) {
        context.reset();
        context.pushViewport(null, context.getScreenArea());
        for (ModularPanel panel : this.screen.getPanelManager().getReverseOpenPanels()) {
            context.updateZ(0);
            WidgetTree.drawTree(panel, context);
        }
        context.updateZ(0);
        context.popViewport(null);
        context.postRenderCallbacks.forEach(callback -> callback.accept(context));

        context.reset();
        context.pushViewport(null, context.getScreenArea());
        for (ModularPanel panel : this.screen.getPanelManager().getReverseOpenPanels()) {
            context.updateZ(FOREGROUND_Z);
            if (panel.isEnabled()) {
                WidgetTree.drawTreeForeground(panel, context);
            }
        }
        context.updateZ(0);
        context.popViewport(null);
    }

    /** @return true if the click landed on a widget */
    public boolean mouseClicked(double x, double y, int mouseButton) {
        int mouseX = toMouseX(x);
        int mouseY = toMouseY(y);
        if (mouseX < 0 || mouseX > this.width || mouseY < 0 || mouseY > this.height) return false;
        this.screen.getContext().updateState(mouseX, mouseY, 0f);
        boolean consumed = this.screen.onMousePressed(mouseButton);
        this.screen.onMouseRelease(mouseButton);
        return consumed;
    }

    /**
     * Gives the wrapper the fields a shown {@link GuiScreen} would have. Item slots draw straight off them - {@code
     * mc} for the carried stack and {@code itemRender} for the icon - and an unshown screen has them all null.
     * <p>
     * They are set by hand rather than through {@code setWorldAndResolution}, which looks like the obvious way to do
     * it but has two side effects this screen must not cause: Forge fires {@code GuiScreenEvent.InitGuiEvent} from
     * it, which is how MUI2 picks up the screen the player is looking at, so our invisible one would be adopted as
     * the current gui; and {@link GuiContainer#initGui()} assigns its container to {@code player.openContainer},
     * taking away whatever gui the player really has open.
     */
    private void initWrapper() {
        Minecraft mc = Minecraft.getMinecraft();
        this.wrapper.mc = mc;
        ObfuscationReflectionHelper.setPrivateValue(GuiScreen.class, this.wrapper, mc.fontRenderer,
                "fontRenderer", "field_146289_q");
        ObfuscationReflectionHelper.setPrivateValue(GuiScreen.class, this.wrapper, mc.getRenderItem(),
                "itemRender", "field_146296_j");
    }

    /** The factor that fits the longer side of the panel into one block. */
    private float scale() {
        return 0.5f / Math.max(this.width / 2f, this.height / 2f);
    }

    private int toMouseX(double x) {
        float halfW = this.width / 2f;
        float halfH = this.height / 2f;
        return (int) ((x / scale()) + (halfW > halfH ? 0 : halfW - halfH));
    }

    private int toMouseY(double y) {
        float halfW = this.width / 2f;
        float halfH = this.height / 2f;
        return (int) ((y / scale()) + (halfH > halfW ? 0 : halfH - halfW));
    }
}
