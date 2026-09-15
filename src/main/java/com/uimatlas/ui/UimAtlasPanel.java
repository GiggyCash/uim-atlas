package com.uimatlas.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Quiet RuneLite-native view. Planning and explanation decisions arrive in an immutable view model. */
public class UimAtlasPanel extends PluginPanel
{
    private static final Color GOLD = new Color(210, 180, 110);
    private static final Color GREEN = new Color(120, 180, 120);
    private static final Color AMBER = new Color(220, 160, 70);
    private final Consumer<String> goalSelected;
    private final JTextArea account = text(ColorScheme.LIGHT_GRAY_COLOR);
    private final JComboBox<PlannerViewModel.GoalOption> goals = new JComboBox<>();
    private final JLabel actionStatus = label(GOLD);
    private final JTextArea next = text(ColorScheme.TEXT_COLOR);
    private final JLabel startCaption = caption("Start");
    private final JTextArea start = text(ColorScheme.LIGHT_GRAY_COLOR);
    private final JTextArea reason = text(ColorScheme.LIGHT_GRAY_COLOR);
    private final JTextArea handoff = text(GOLD);
    private final JButton whyToggle = toggle("Why this?");
    private final JButton optionsToggle = toggle("Other options");
    private final JPanel why = vertical();
    private final JPanel options = vertical();
    private PlannerViewModel displayed;
    private boolean rendering;

    public UimAtlasPanel()
    {
        this(ignored -> { });
    }

    public UimAtlasPanel(Consumer<String> goalSelected)
    {
        this.goalSelected = goalSelected;
        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
        JPanel content = vertical();
        JLabel title = label(GOLD);
        title.setText("UIM ATLAS");
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        content.add(title);
        content.add(account);
        content.add(Box.createVerticalStrut(10));
        content.add(caption("Goal"));
        goals.setFocusable(false);
        goals.addActionListener(event ->
        {
            if (!rendering)
            {
                PlannerViewModel.GoalOption selected = (PlannerViewModel.GoalOption) goals.getSelectedItem();
                goalSelected.accept(selected == null || selected.getId().isEmpty() ? null : selected.getId());
            }
        });
        content.add(goals);
        content.add(Box.createVerticalStrut(10));
        content.add(new JSeparator());
        content.add(Box.createVerticalStrut(10));
        content.add(caption("Next"));
        content.add(next);
        content.add(Box.createVerticalStrut(8));
        content.add(startCaption);
        content.add(start);
        content.add(Box.createVerticalStrut(8));
        content.add(caption("Reason"));
        content.add(reason);
        content.add(Box.createVerticalStrut(8));
        content.add(caption("Status"));
        content.add(actionStatus);
        content.add(handoff);
        content.add(Box.createVerticalStrut(8));
        content.add(whyToggle);
        why.setVisible(false);
        content.add(why);
        content.add(optionsToggle);
        options.setVisible(false);
        content.add(options);
        whyToggle.addActionListener(event -> reveal(why, whyToggle, "Why this?"));
        optionsToggle.addActionListener(event -> reveal(options, optionsToggle, "Other options"));
        add(content, BorderLayout.NORTH);
    }

    /** Called only on Swing's event-dispatch thread. */
    public void render(PlannerViewModel model)
    {
        if (model.equals(displayed))
        {
            return;
        }
        displayed = model;
        account.setText(model.getAccount() + " · " + model.getAccountState());
        rendering = true;
        goals.removeAllItems();
        goals.addItem(new PlannerViewModel.GoalOption("", "Select a goal…"));
        model.getGoals().forEach(goals::addItem);
        for (int index = 0; index < goals.getItemCount(); index++)
        {
            if (goals.getItemAt(index).getId().equals(model.getSelectedGoalId()))
            {
                goals.setSelectedIndex(index);
                break;
            }
        }
        rendering = false;
        next.setText(model.getNext());
        boolean hasStart = model.getStart() != null;
        startCaption.setVisible(hasStart);
        start.setVisible(hasStart);
        start.setText(hasStart ? model.getStart() : "");
        reason.setText(model.getReason());
        actionStatus.setText(model.getStatus());
        actionStatus.setForeground(statusColor(model.getStatus()));
        handoff.setText(model.getHandoff() == null ? "" : model.getHandoff());
        rebuild(why, model.getWhy());
        whyToggle.setVisible(!model.getWhy().isEmpty());
        rebuildOptions(model.getAlternatives());
        optionsToggle.setVisible(!model.getAlternatives().isEmpty());
        revalidate();
        repaint();
    }

    public PlannerViewModel getDisplayed()
    {
        return displayed;
    }

    public void selectGoal(String goalId)
    {
        for (int index = 0; index < goals.getItemCount(); index++)
        {
            if (goals.getItemAt(index).getId().equals(goalId == null ? "" : goalId))
            {
                goals.setSelectedIndex(index);
                return;
            }
        }
        throw new IllegalArgumentException("Unknown displayed goal ID: " + goalId);
    }

    private void rebuildOptions(java.util.List<PlannerViewModel.Option> values)
    {
        options.removeAll();
        for (PlannerViewModel.Option value : values)
        {
            JTextArea title = text(ColorScheme.TEXT_COLOR);
            title.setText(value.getTitle());
            options.add(title);
            JLabel status = label(statusColor(value.getStatus()));
            status.setText(value.getStatus());
            options.add(status);
            JTextArea detail = text(ColorScheme.LIGHT_GRAY_COLOR);
            detail.setText(value.getReason());
            detail.setBorder(BorderFactory.createEmptyBorder(0, 0, 7, 0));
            options.add(detail);
        }
    }

    private void rebuild(JPanel panel, java.util.List<String> values)
    {
        panel.removeAll();
        for (String value : values)
        {
            JTextArea line = text(ColorScheme.LIGHT_GRAY_COLOR);
            line.setText("• " + value);
            line.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
            panel.add(line);
        }
    }

    private void reveal(JPanel panel, JButton button, String title)
    {
        panel.setVisible(!panel.isVisible());
        button.setText((panel.isVisible() ? "▾ " : "▸ ") + title);
        revalidate();
    }

    private static JPanel vertical()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JButton toggle(String text)
    {
        JButton button = new JButton("▸ " + text);
        button.setFocusable(false);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setForeground(GOLD);
        button.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        button.setContentAreaFilled(false);
        return button;
    }

    private static JLabel caption(String text)
    {
        JLabel label = label(ColorScheme.LIGHT_GRAY_COLOR);
        label.setText(text);
        return label;
    }

    private static JLabel label(Color color)
    {
        JLabel label = new JLabel();
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static Color statusColor(String status)
    {
        return status.equals("SETUP READY") || status.equals("COMPLETE") ? GREEN
            : status.equals("NEEDS INFO") ? AMBER : GOLD;
    }

    private static JTextArea text(Color color)
    {
        JTextArea text = new WrappingTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new JLabel().getFont());
        text.setForeground(color);
        text.setBorder(null);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        return text;
    }

    /** Reflows plain text to the actual parent width instead of assuming a sidebar pixel width in HTML. */
    private static final class WrappingTextArea extends JTextArea
    {
        @Override
        public Dimension getPreferredSize()
        {
            if (getParent() != null && getParent().getWidth() > 0)
            {
                setSize(getParent().getWidth(), Short.MAX_VALUE);
            }
            return super.getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }

        @Override
        public Dimension getMinimumSize()
        {
            Dimension preferred = getPreferredSize();
            return new Dimension(0, preferred.height);
        }
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
