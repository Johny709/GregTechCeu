package gregtech.api.metatileentity.multiblock.ui;

import com.cleanroommc.modularui.api.value.IDoubleValue;
import com.cleanroommc.modularui.drawable.UITexture;
import com.cleanroommc.modularui.screen.RichTooltip;
import com.cleanroommc.modularui.value.DoubleValue;
import com.cleanroommc.modularui.widgets.ProgressWidget;

import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

public final class TemplateBarBuilder {

    ProgressWidget widget = new ProgressWidget();
    private UITexture texture;

    TemplateBarBuilder() {}

    public TemplateBarBuilder progress(DoubleSupplier supplier) {
        return value(new DoubleValue.Dynamic(supplier, d -> {}));
    }

    public TemplateBarBuilder value(IDoubleValue<?> value) {
        this.widget.value(value);
        return this;
    }

    public TemplateBarBuilder texture(UITexture texture) {
        this.texture = texture;
        return this;
    }

    public TemplateBarBuilder tooltipBuilder(Consumer<RichTooltip> consumer) {
        this.widget.tooltipAutoUpdate(true);
        this.widget.tooltipBuilder(consumer);
        return this;
    }

    /**
     * @param imageSize the bar's length along the direction it fills in, in pixels. It has to be passed in here
     *                  because the bar is only as wide as the row it ends up in, and because a {@link ProgressWidget}
     *                  left to work its own size out never recovers: it latches the size on its first
     *                  {@code onResized}, which MUI2 fires while the widget initialises and its area is still empty,
     *                  leaving the fill quantisation dividing by zero and the filled half of the bar undrawn.
     */
    ProgressWidget build(int imageSize) {
        if (this.texture != null) {
            this.widget.texture(this.texture, imageSize);
        }
        return this.widget;
    }
}
