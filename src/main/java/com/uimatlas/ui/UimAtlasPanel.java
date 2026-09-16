package com.uimatlas.ui;

import com.uimatlas.recommendation.MethodDefinition;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import net.runelite.client.ui.PluginPanel;

/** Restrained action surface. It renders immutable semantics and emits goal/route intent only. */
public class UimAtlasPanel extends PluginPanel
{
    private final Consumer<String> goalSelected;
    private final Consumer<MethodDefinition.RouteTarget> routeRequested;
    private final JPanel content = vertical();
    private final JTextArea goalName = text(AtlasTheme.TEXT, AtlasTheme.GOAL);
    private final JLabel progress = label(AtlasTheme.MUTED, AtlasTheme.SMALL);
    private final JComboBox<PlannerViewModel.GoalOption> goals = new JComboBox<>();
    private final JLabel eyebrow = label(AtlasTheme.ACCENT, AtlasTheme.LABEL);
    private final JTextArea next = text(AtlasTheme.TEXT, AtlasTheme.TITLE);
    private final JTextArea method = text(AtlasTheme.ACCENT, AtlasTheme.BODY);
    private final JTextArea destination = text(AtlasTheme.MUTED, AtlasTheme.SMALL);
    private final JLabel actionStatus = label(AtlasTheme.POSITIVE, AtlasTheme.LABEL);
    private final JTextArea reason = text(AtlasTheme.SECONDARY, AtlasTheme.BODY);
    private final AtlasButton route = new AtlasButton("Route");
    private final JLabel routeAttribution = label(AtlasTheme.MUTED, AtlasTheme.SMALL);
    private final JLabel routeFeedback = label(AtlasTheme.MUTED, AtlasTheme.SMALL);
    private final JButton whyToggle = toggle("Why this?");
    private final JButton optionsToggle = toggle("Other options");
    private final JPanel why = vertical();
    private final JPanel options = vertical();
    private final RoundedPanel primary = new RoundedPanel(AtlasTheme.ELEVATED);
    private PlannerViewModel displayed;
    private boolean rendering;

    public UimAtlasPanel()
    {
        this(ignored -> { }, ignored -> { });
    }

    public UimAtlasPanel(Consumer<String> goalSelected)
    {
        this(goalSelected, ignored -> { });
    }

    public UimAtlasPanel(Consumer<String> goalSelected,
        Consumer<MethodDefinition.RouteTarget> routeRequested)
    {
        this.goalSelected = goalSelected;
        this.routeRequested = routeRequested;
        setLayout(new BorderLayout());
        setBackground(AtlasTheme.BACKGROUND);
        setBorder(AtlasTheme.padding(AtlasTheme.SPACE_4, AtlasTheme.SPACE_3,
            AtlasTheme.SPACE_4, AtlasTheme.SPACE_3));

        goalName.setName("goalName");
        progress.setName("goalProgress");
        content.add(goalName);
        content.add(progress);
        styleGoalSelector();
        content.add(goals);
        content.add(Box.createVerticalStrut(AtlasTheme.SPACE_3));

        primary.setLayout(new BoxLayout(primary, BoxLayout.Y_AXIS));
        primary.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_3, AtlasTheme.SPACE_3,
            AtlasTheme.SPACE_3, AtlasTheme.SPACE_3));
        eyebrow.setText("NEXT UP");
        primary.add(eyebrow);
        primary.add(Box.createVerticalStrut(AtlasTheme.SPACE_2));
        next.setName("primaryTitle");
        primary.add(next);
        method.setName("primaryMethod");
        method.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_1, 0, 0, 0));
        primary.add(method);
        destination.setName("primaryDestination");
        destination.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_1, 0, 0, 0));
        primary.add(destination);
        primary.add(Box.createVerticalStrut(AtlasTheme.SPACE_2));
        primary.add(actionStatus);

        JPanel routeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, AtlasTheme.SPACE_2));
        routeRow.setOpaque(false);
        routeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        route.setName("routeAction");
        route.addActionListener(event ->
        {
            if (displayed != null && displayed.getRouteTarget() != null)
            {
                route.setEnabled(false);
                routeFeedback.setText("Requesting route…");
                routeFeedback.setVisible(true);
                routeRequested.accept(displayed.getRouteTarget());
            }
        });
        routeRow.add(route);
        routeAttribution.setText("  via Shortest Path");
        routeRow.add(routeAttribution);
        primary.add(routeRow);
        primary.add(routeFeedback);
        reason.setName("primaryReason");
        reason.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_2, 0, 0, 0));
        primary.add(reason);
        content.add(primary);
        content.add(Box.createVerticalStrut(AtlasTheme.SPACE_2));

        whyToggle.setName("whyToggle");
        optionsToggle.setName("optionsToggle");
        content.add(whyToggle);
        why.setVisible(false);
        why.setBorder(AtlasTheme.padding(0, AtlasTheme.SPACE_2, AtlasTheme.SPACE_2, 0));
        content.add(why);
        content.add(optionsToggle);
        options.setVisible(false);
        options.setBorder(AtlasTheme.padding(0, 0, AtlasTheme.SPACE_2, 0));
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
        renderGoal(model);
        eyebrow.setText(model.getSurfaceState() == PlannerViewModel.SurfaceState.READY ? "NEXT UP" : "ATLAS");
        next.setText(model.getNext());
        showText(method, model.getMethod());
        showText(destination, model.getStart());
        reason.setText(model.getReason());
        actionStatus.setText(model.getStatus());
        actionStatus.setForeground(statusColor(model.getStatus()));
        boolean routeable = model.getSurfaceState() == PlannerViewModel.SurfaceState.READY
            && model.getMethod() != null && model.getRouteTarget() != null;
        route.setVisible(routeable);
        routeAttribution.setVisible(routeable);
        route.setEnabled(routeable);
        routeFeedback.setText("");
        routeFeedback.setVisible(false);
        rebuildWhy(model.getWhy());
        rebuildOptions(model.getAlternatives());
        collapse(why, whyToggle, "Why this?");
        collapse(options, optionsToggle, "Other options");
        whyToggle.setVisible(!model.getWhy().isEmpty());
        optionsToggle.setVisible(!model.getAlternatives().isEmpty());
        revalidate();
        repaint();
    }

    public void routeResult(MethodDefinition.RouteTarget target, boolean requested)
    {
        // Alternative requests have no feedback on the primary card; ignore results after reset too.
        if (displayed == null || !target.equals(displayed.getRouteTarget()) || route.isEnabled())
        {
            return;
        }
        route.setEnabled(displayed != null && displayed.getRouteTarget() != null);
        routeFeedback.setText(requested ? "Route requested." : "Route unavailable right now.");
        routeFeedback.setForeground(requested ? AtlasTheme.POSITIVE : AtlasTheme.MUTED);
        routeFeedback.setVisible(true);
        revalidate();
        repaint();
    }

    public PlannerViewModel getDisplayed()
    {
        return displayed;
    }

    public boolean isRouteActionVisible()
    {
        return route.isVisible();
    }

    public boolean isGoalSelectorVisible()
    {
        return goals.isVisible();
    }

    public boolean isWhyExpanded()
    {
        return why.isVisible();
    }

    public boolean isOptionsExpanded()
    {
        return options.isVisible();
    }

    public void clickRoute()
    {
        route.doClick();
    }

    public void toggleWhy()
    {
        whyToggle.doClick();
    }

    public void toggleOptions()
    {
        optionsToggle.doClick();
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

    private void renderGoal(PlannerViewModel model)
    {
        boolean single = model.getGoals().size() == 1;
        String name = model.getGoalName();
        if (name == null && single)
        {
            name = model.getGoals().get(0).getDisplayName();
        }
        showText(goalName, name == null ? "GOAL" : name.toUpperCase(java.util.Locale.ROOT));
        progress.setText(model.getGoalProgress() == null ? "" : model.getGoalProgress());
        progress.setVisible(model.getGoalProgress() != null);
        rendering = true;
        goals.removeAllItems();
        if (!single && model.getSelectedGoalId() == null)
        {
            goals.addItem(new PlannerViewModel.GoalOption("", "Select a goal…"));
        }
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
        goals.setVisible(!single && model.getGoals().size() > 1);
    }

    private void styleGoalSelector()
    {
        goals.setName("goalSelector");
        goals.setFocusable(false);
        goals.setFont(AtlasTheme.BODY);
        goals.setForeground(AtlasTheme.TEXT);
        goals.setBackground(AtlasTheme.SUBTLE);
        goals.setBorder(BorderFactory.createLineBorder(AtlasTheme.BORDER));
        goals.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        goals.setAlignmentX(Component.LEFT_ALIGNMENT);
        goals.addActionListener(event ->
        {
            if (!rendering)
            {
                PlannerViewModel.GoalOption selected = (PlannerViewModel.GoalOption) goals.getSelectedItem();
                goalSelected.accept(selected == null || selected.getId().isEmpty() ? null : selected.getId());
            }
        });
    }

    private void rebuildWhy(List<PlannerViewModel.Detail> values)
    {
        why.removeAll();
        for (PlannerViewModel.Detail value : values)
        {
            JLabel heading = label(AtlasTheme.MUTED, AtlasTheme.LABEL);
            heading.setText(value.getLabel().toUpperCase(java.util.Locale.ROOT));
            heading.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_2, 0, 0, 0));
            why.add(heading);
            JTextArea detail = text(AtlasTheme.SECONDARY, AtlasTheme.BODY);
            detail.setText(value.getText());
            why.add(detail);
        }
    }

    private void rebuildOptions(List<PlannerViewModel.Option> values)
    {
        options.removeAll();
        for (PlannerViewModel.Option value : values)
        {
            RoundedPanel row = new RoundedPanel(AtlasTheme.SUBTLE);
            row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
            row.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_2, AtlasTheme.SPACE_2,
                AtlasTheme.SPACE_2, AtlasTheme.SPACE_2));
            JTextArea title = text(AtlasTheme.TEXT, AtlasTheme.GOAL);
            title.setText(value.getTitle());
            row.add(title);
            if (value.getMethod() != null)
            {
                JTextArea methodName = text(AtlasTheme.MUTED, AtlasTheme.SMALL);
                methodName.setText(value.getMethod());
                row.add(methodName);
            }
            JLabel status = label(statusColor(value.getStatus()), AtlasTheme.LABEL);
            status.setText(value.getStatus());
            status.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_1, 0, 0, 0));
            row.add(status);
            if (value.getRouteTarget() != null)
            {
                JButton action = toggle("Route");
                action.setText("Route →");
                action.setName("alternativeRouteAction");
                action.setFont(AtlasTheme.SMALL);
                action.addActionListener(event -> routeRequested.accept(value.getRouteTarget()));
                row.add(action);
            }
            JTextArea detail = text(AtlasTheme.MUTED, AtlasTheme.SMALL);
            detail.setText(value.getReason());
            detail.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_1, 0, 0, 0));
            row.add(detail);
            options.add(row);
            options.add(Box.createVerticalStrut(AtlasTheme.SPACE_2));
        }
    }

    private void reveal(JPanel panel, JButton button, String title)
    {
        boolean visible = !panel.isVisible();
        panel.setVisible(visible);
        button.setText((visible ? "▾  " : "›  ") + title);
        revalidate();
        repaint();
    }

    private void collapse(JPanel panel, JButton button, String title)
    {
        panel.setVisible(false);
        button.setText("›  " + title);
    }

    private static void showText(JTextArea area, String value)
    {
        area.setText(value == null ? "" : value);
        area.setVisible(value != null && !value.isEmpty());
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
        JButton button = new JButton("›  " + text);
        button.setFocusable(false);
        button.setHorizontalAlignment(JButton.LEFT);
        button.setFont(AtlasTheme.BODY);
        button.setForeground(AtlasTheme.SECONDARY);
        button.setBorder(AtlasTheme.padding(AtlasTheme.SPACE_2, 0, AtlasTheme.SPACE_2, 0));
        button.setContentAreaFilled(false);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    private static JLabel label(Color color, java.awt.Font font)
    {
        JLabel label = new JLabel();
        label.setForeground(color);
        label.setFont(font);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static Color statusColor(String status)
    {
        if (status.equals("SETUP READY") || status.equals("GOAL COMPLETE"))
        {
            return AtlasTheme.POSITIVE;
        }
        if (status.equals("NEEDS INFO") || status.equals("UNSUPPORTED"))
        {
            return AtlasTheme.CAUTION;
        }
        return AtlasTheme.ACCENT;
    }

    private static JTextArea text(Color color, java.awt.Font font)
    {
        JTextArea text = new WrappingTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(font);
        text.setForeground(color);
        text.setBorder(null);
        text.setAlignmentX(Component.LEFT_ALIGNMENT);
        return text;
    }

    /** Reflows plain text to the actual parent width rather than relying on fixed-width HTML. */
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
            return new Dimension(0, getPreferredSize().height);
        }
    }

    private static final class RoundedPanel extends JPanel
    {
        private final Color fill;

        private RoundedPanel(Color fill)
        {
            this.fill = fill;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(fill);
            copy.fillRoundRect(0, 0, getWidth(), getHeight(), AtlasTheme.RADIUS, AtlasTheme.RADIUS);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class AtlasButton extends JButton
    {
        private AtlasButton(String text)
        {
            super(text);
            setFont(AtlasTheme.GOAL);
            setForeground(new Color(35, 31, 23));
            setBorder(AtlasTheme.padding(6, 12, 6, 12));
            setContentAreaFilled(false);
            setFocusPainted(false);
            setRolloverEnabled(true);
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            Color fill = !isEnabled() ? AtlasTheme.MUTED
                : getModel().isPressed() ? AtlasTheme.BUTTON_PRESSED
                : getModel().isRollover() ? AtlasTheme.BUTTON_HOVER : AtlasTheme.ACCENT;
            Graphics2D copy = (Graphics2D) graphics.create();
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(fill);
            copy.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            copy.dispose();
            super.paintComponent(graphics);
        }
    }

    public static BufferedImage navigationIcon()
    {
        BufferedImage icon = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = icon.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(AtlasTheme.ACCENT);
        graphics.drawOval(3, 3, 17, 17);
        graphics.fillPolygon(new int[]{12, 8, 15}, new int[]{4, 15, 12}, 3);
        graphics.setColor(AtlasTheme.MUTED);
        graphics.fillPolygon(new int[]{12, 8, 15}, new int[]{20, 15, 12}, 3);
        graphics.dispose();
        return icon;
    }
}
