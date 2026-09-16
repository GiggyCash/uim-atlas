package com.uimatlas.ui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** Small Atlas-specific token set layered on RuneLite's native theme. */
final class AtlasTheme
{
    static final Color BACKGROUND = ColorScheme.DARK_GRAY_COLOR;
    static final Color ELEVATED = new Color(48, 48, 48);
    static final Color SUBTLE = new Color(43, 43, 43);
    static final Color TEXT = new Color(226, 226, 226);
    static final Color SECONDARY = ColorScheme.TEXT_COLOR;
    static final Color MUTED = ColorScheme.LIGHT_GRAY_COLOR;
    static final Color ACCENT = new Color(210, 174, 96);
    static final Color POSITIVE = new Color(117, 177, 126);
    static final Color CAUTION = new Color(218, 158, 76);
    static final Color BORDER = ColorScheme.BORDER_COLOR;
    static final Color BUTTON_HOVER = new Color(224, 188, 108);
    static final Color BUTTON_PRESSED = new Color(184, 147, 70);

    static final int SPACE_1 = 4;
    static final int SPACE_2 = 8;
    static final int SPACE_3 = 12;
    static final int SPACE_4 = 16;
    static final int RADIUS = 8;

    static final Font BODY = FontManager.getDefaultFont().deriveFont(14f);
    static final Font SMALL = FontManager.getDefaultFont().deriveFont(12f);
    static final Font LABEL = FontManager.getDefaultBoldFont().deriveFont(11f);
    static final Font TITLE = FontManager.getDefaultBoldFont().deriveFont(18f);
    static final Font GOAL = FontManager.getDefaultBoldFont().deriveFont(14f);

    private AtlasTheme()
    {
    }

    static Border padding(int top, int left, int bottom, int right)
    {
        return BorderFactory.createEmptyBorder(top, left, bottom, right);
    }
}
