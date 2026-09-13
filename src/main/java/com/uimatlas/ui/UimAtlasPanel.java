package com.uimatlas.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class UimAtlasPanel extends PluginPanel
{
    private static final Color GOLD = new Color(210, 180, 110);
    private final JLabel account = new JLabel();
    private final JLabel status = new JLabel();
    private final JLabel totalLevel = new JLabel();
    private final JLabel inventory = new JLabel();
    private AccountSummary displayed;

    public UimAtlasPanel()
    {
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        JPanel content = new JPanel(new GridLayout(0, 1, 0, 6));
        content.setOpaque(false);
        JLabel title = new JLabel("UIM ATLAS");
        title.setForeground(GOLD);
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        content.add(title);
        addRow(content, "Account", account);
        addRow(content, "State", status);
        addRow(content, "Total level", totalLevel);
        addRow(content, "Inventory", inventory);
        add(content, BorderLayout.NORTH);
    }

    private void addRow(JPanel content, String name, JLabel value)
    {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        JLabel label = new JLabel(name);
        label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
        value.setForeground(ColorScheme.TEXT_COLOR);
        row.add(label, BorderLayout.NORTH);
        row.add(value, BorderLayout.SOUTH);
        content.add(row);
    }

    /** Called only on Swing's event-dispatch thread. */
    public void render(AccountSummary summary)
    {
        if (summary.equals(displayed))
        {
            return;
        }
        displayed = summary;
        account.setText(summary.getAccount());
        status.setText(summary.getStatus());
        totalLevel.setText(summary.getTotalLevel());
        inventory.setText(summary.getInventory());
    }

    public static BufferedImage navigationIcon()
    {
        BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(GOLD);
        graphics.drawOval(3, 3, 17, 17);
        graphics.fillPolygon(new int[]{12, 8, 15}, new int[]{4, 15, 12}, 3);
        graphics.setColor(ColorScheme.LIGHT_GRAY_COLOR);
        graphics.fillPolygon(new int[]{12, 8, 15}, new int[]{20, 15, 12}, 3);
        graphics.dispose();
        return icon;
    }
}
