package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/** Draws the tvOS style lighter band behind the first (dock) row and adds a gap below it. */
public class DockDecoration extends RecyclerView.ItemDecoration {

    /**
     * The band's room beside its outer tiles, so the focus zoom and glass (about 14dp past the tile) stay clear of
     * the band's edge. MainActivity pads the grid by BAND_INSET_DP, since the band only draws inside it.
     */
    static final float BAND_SIDE_DP = 20;
    static final float BAND_INSET_DP = 14;
    /** The band's room above and below its tiles, and its corner radius; the weather widget's glass uses the same. */
    static final float BAND_PAD_DP = 14;
    static final float BAND_RADIUS_DP = 26;
    /** The corners of the weather widget and the now playing card: a rounded rectangle, not the band's pill. */
    static final float CARD_RADIUS_DP = 7;
    /** The band's colour unless a theme sets dock_band_color, and its edge line. */
    static final int BAND_COLOR = 0xFF141416;
    static final int EDGE_COLOR = 0x1FFFFFFF;

    private final int dockSize;
    private final float density;
    private final Paint band = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    /** Extra band below the tiles for their names (always_show_names), in px. */
    private final float namesRoom;

    /**
     * @param translucent the cinematic shelf's band: the artwork and the glow show through it.
     * @param names       the dock tiles show their names, so the band reaches down over them.
     * @param bandColor   the theme's dock_band_color, alpha included; 0 keeps the built-in colour.
     */
    DockDecoration(int dockSize, float density, boolean translucent, boolean names, int bandColor) {
        this.dockSize = dockSize;
        this.density = density;
        this.namesRoom = names ? 22 * density : 0;
        band.setColor(bandColor != 0 ? bandColor : translucent ? 0x8C121014 : BAND_COLOR);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
        edge.setColor(translucent ? 0x26FFFFFF : EDGE_COLOR);
    }

    /**
     * How far the band reaches past its outer tiles: BAND_SIDE_DP, less where the grid's edge is closer (the band only
     * draws inside the grid). Gaps are from the outer tiles to the grid's edges, in px. MainActivity lines the clock
     * and the weather widget up with the result.
     */
    static float sideRoom(float leftGap, float rightGap, float density) {
        return Math.max(0f, Math.min(BAND_SIDE_DP * density, Math.min(leftGap, rightGap) - density));
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        if (pos >= 0 && pos < dockSize) {
            outRect.top = (int) (6 * density);
            outRect.bottom = (int) (12 * density);
        }
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        boolean any = false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos < 0 || pos >= dockSize) continue;
            RecyclerView.ViewHolder vh = parent.getChildViewHolder(child);
            View card = vh instanceof AppAdapter.Holder ? ((AppAdapter.Holder) vh).card : child.findViewById(R.id.card);
            if (card == null) continue;
            float l = child.getLeft() + card.getLeft();
            float t = child.getTop() + card.getTop() + child.getTranslationY();
            float r = l + card.getWidth();
            float b = t + card.getHeight();
            if (!any) {
                rect.set(l, t, r, b);
                any = true;
            } else {
                rect.union(l, t, r, b);
            }
        }
        if (!any) return;
        float pad = BAND_PAD_DP * density;
        // The band can only draw inside the grid, and the first and last dock tiles sit 12dp in from its
        // edges: a full 14dp at the sides sliced the rounded ends flat (#454). Keep the sides, with the
        // 1dp edge line, inside the grid; the top and bottom have room.
        float side = sideRoom(rect.left, parent.getWidth() - rect.right, density);
        rect.inset(-side, -pad);
        rect.bottom += namesRoom;
        float radius = BAND_RADIUS_DP * density;
        c.drawRoundRect(rect, radius, radius, band);
        c.drawRoundRect(rect, radius, radius, edge);
    }
}
