package com.hussaincodes.tidebound;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.Point;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Tidebound: a little lighthouse puzzle about sending a signal home. */
public final class TideboundApp {
    private static final Color NIGHT = new Color(12, 24, 34);
    private static final Color PANEL = new Color(22, 40, 52);
    private static final Color PANEL_LIGHT = new Color(32, 54, 66);
    private static final Color TEXT = new Color(238, 241, 226);
    private static final Color MUTED = new Color(157, 179, 181);
    private static final Color GOLD = new Color(245, 193, 91);
    private static final Color AQUA = new Color(98, 213, 201);
    private static final Color REEF = new Color(96, 109, 112);
    private static final List<Puzzle> PUZZLES = Puzzle.all();

    private final GameStore store;
    private final Map<Integer, Integer> bestScores;
    private final Map<Point, Boolean> mirrors = new HashMap<>();
    private final List<Point> trace = new ArrayList<>();
    private final Set<Point> activeMirrors = new HashSet<>();
    private final Board board = new Board();
    private final JLabel levelLabel = label("LEVEL 01 / 08", 12, GOLD, Font.BOLD);
    private final JLabel titleLabel = label("First Light", 24, TEXT, Font.BOLD);
    private final JLabel storyLabel = label("A quiet start. Bend the beam around the old breakwater.", 13, MUTED, Font.PLAIN);
    private final JLabel statusLabel = label("Rotate the lit mirrors, then send the signal.", 13, TEXT, Font.PLAIN);
    private final JLabel movesValue = label("0", 22, TEXT, Font.BOLD);
    private final JLabel hintsValue = label("0", 22, TEXT, Font.BOLD);
    private final JLabel scoreValue = label("—", 22, GOLD, Font.BOLD);
    private final JLabel bestValue = label("Not yet", 15, AQUA, Font.BOLD);
    private final JLabel recordValue = label("0 tries saved", 12, MUTED, Font.PLAIN);
    private final JLabel objectiveValue = label("Reach the ship marked with the gold beacon.", 13, TEXT, Font.PLAIN);
    private final JComboBox<String> levelPicker = new JComboBox<>();
    private final JButton hintButton = button("Reveal a mirror  ·  −18 points", false);
    private final JButton traceButton = button("SEND THE SIGNAL   →", true);
    private final JButton nextButton = button("Next channel  →", true);
    private final JFrame frame = new JFrame("Tidebound — Signal Keeper");

    private int levelIndex;
    private int moves;
    private int hints;
    private int unlocked;
    private boolean solved;

    private TideboundApp(GameStore store, Map<Integer, Integer> bestScores, int attempts) {
        this.store = store;
        this.bestScores = bestScores;
        unlocked = Math.min(PUZZLES.size(), Math.max(1, bestScores.keySet().stream().mapToInt(Integer::intValue).max().orElse(1) + 1));
        recordValue.setText(attempts + (attempts == 1 ? " try saved locally" : " tries saved locally"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 730));
        frame.setSize(1160, 790);
        frame.setLocationRelativeTo(null);
        frame.setContentPane(buildScreen());
        loadLevel(0);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) { }
        SwingUtilities.invokeLater(() -> {
            try {
                GameStore store = new GameStore();
                TideboundApp app = new TideboundApp(store, store.loadBestScores(), store.totalAttempts());
                app.frame.setVisible(true);
            } catch (Exception error) {
                error.printStackTrace();
                JOptionPane.showMessageDialog(null,
                    "Tidebound could not open its local SQLite save file.\n\n" + error.getMessage(),
                    "Save data unavailable", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private JPanel buildScreen() {
        JPanel root = new JPanel(new BorderLayout(22, 18));
        root.setBackground(NIGHT);
        root.setBorder(new EmptyBorder(25, 30, 24, 30));
        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildMain(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(16, 0));
        header.setOpaque(false);
        JPanel brand = new JPanel();
        brand.setOpaque(false);
        brand.setLayout(new BoxLayout(brand, BoxLayout.Y_AXIS));
        JLabel eyebrow = label("TIDEB0UND  /  SIGNAL KEEPER", 11, AQUA, Font.BOLD);
        eyebrow.setText("T I D E B O U N D    /    SIGNAL KEEPER");
        JLabel title = label("Keep one light burning.", 30, TEXT, Font.BOLD);
        JLabel subtitle = label("A hand-built harbor puzzle. Every safe return begins with a clear signal.", 13, MUTED, Font.PLAIN);
        brand.add(eyebrow);
        brand.add(Box.createVerticalStrut(6));
        brand.add(title);
        brand.add(Box.createVerticalStrut(4));
        brand.add(subtitle);
        header.add(brand, BorderLayout.WEST);

        JPanel progress = new JPanel();
        progress.setOpaque(false);
        progress.setLayout(new BoxLayout(progress, BoxLayout.Y_AXIS));
        levelLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        progress.add(levelLabel);
        JLabel legend = label("✦  best route saved", 12, GOLD, Font.PLAIN);
        legend.setHorizontalAlignment(SwingConstants.RIGHT);
        progress.add(Box.createVerticalStrut(8));
        progress.add(legend);
        header.add(progress, BorderLayout.EAST);
        return header;
    }

    private JPanel buildMain() {
        JPanel main = new JPanel(new BorderLayout(20, 0));
        main.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout(0, 10));
        left.setOpaque(false);
        left.add(buildPuzzleHeader(), BorderLayout.NORTH);
        board.setPreferredSize(new Dimension(548, 548));
        left.add(board, BorderLayout.CENTER);
        main.add(left, BorderLayout.CENTER);
        main.add(buildSidebar(), BorderLayout.EAST);
        return main;
    }

    private JPanel buildPuzzleHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JPanel names = new JPanel();
        names.setOpaque(false);
        names.setLayout(new BoxLayout(names, BoxLayout.Y_AXIS));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        storyLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        names.add(titleLabel);
        names.add(Box.createVerticalStrut(4));
        names.add(storyLabel);
        panel.add(names, BorderLayout.WEST);

        JPanel pickerPanel = new JPanel(new BorderLayout(6, 5));
        pickerPanel.setOpaque(false);
        JLabel choose = label("CHARTED CHANNELS", 10, MUTED, Font.BOLD);
        choose.setHorizontalAlignment(SwingConstants.RIGHT);
        levelPicker.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        levelPicker.setBackground(PANEL);
        levelPicker.setForeground(TEXT);
        for (int i = 0; i < PUZZLES.size(); i++) levelPicker.addItem(String.format("%02d  ·  %s", i + 1, PUZZLES.get(i).name()));
        levelPicker.addActionListener(event -> {
            if (levelPicker.getSelectedIndex() >= 0 && levelPicker.getSelectedIndex() < unlocked) loadLevel(levelPicker.getSelectedIndex());
            else levelPicker.setSelectedIndex(levelIndex);
        });
        pickerPanel.add(choose, BorderLayout.NORTH);
        pickerPanel.add(levelPicker, BorderLayout.CENTER);
        panel.add(pickerPanel, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setOpaque(false);
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JPanel objective = card();
        objective.add(label("YOUR MISSION", 10, AQUA, Font.BOLD));
        objective.add(Box.createVerticalStrut(10));
        objectiveValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        objective.add(objectiveValue);
        objective.add(Box.createVerticalStrut(10));
        JLabel controls = label("Click a mirror to rotate it. Reefs stop the beam.", 12, MUTED, Font.PLAIN);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        objective.add(controls);
        sidebar.add(objective);
        sidebar.add(Box.createVerticalStrut(12));

        JPanel stats = card();
        stats.add(label("THIS ATTEMPT", 10, AQUA, Font.BOLD));
        stats.add(Box.createVerticalStrut(10));
        JPanel statLine = new JPanel(new BorderLayout(0, 0));
        statLine.setOpaque(false);
        statLine.add(statBlock("MIRROR TURNS", movesValue), BorderLayout.WEST);
        statLine.add(statBlock("HINTS", hintsValue), BorderLayout.EAST);
        statLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(statLine);
        stats.add(Box.createVerticalStrut(10));
        stats.add(label("CURRENT SIGNAL SCORE", 10, MUTED, Font.BOLD));
        scoreValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(scoreValue);
        stats.add(Box.createVerticalStrut(4));
        JPanel bestLine = new JPanel(new BorderLayout());
        bestLine.setOpaque(false);
        bestLine.add(label("PERSONAL BEST", 10, MUTED, Font.BOLD), BorderLayout.WEST);
        bestLine.add(bestValue, BorderLayout.EAST);
        bestLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        stats.add(bestLine);
        sidebar.add(stats);
        sidebar.add(Box.createVerticalStrut(12));

        JPanel actions = card();
        statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusLabel.setVerticalAlignment(SwingConstants.TOP);
        actions.add(statusLabel);
        actions.add(Box.createVerticalStrut(12));
        hintButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        hintButton.addActionListener(event -> revealMirror());
        actions.add(hintButton);
        actions.add(Box.createVerticalStrut(8));
        traceButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        traceButton.addActionListener(event -> sendSignal());
        actions.add(traceButton);
        actions.add(Box.createVerticalStrut(8));
        nextButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        nextButton.addActionListener(event -> {
            if (levelIndex + 1 < unlocked) loadLevel(levelIndex + 1);
        });
        nextButton.setEnabled(false);
        actions.add(nextButton);
        sidebar.add(actions);
        sidebar.add(Box.createVerticalStrut(12));

        JPanel saved = card();
        saved.add(label("KEEPING A LOG", 10, AQUA, Font.BOLD));
        saved.add(Box.createVerticalStrut(7));
        JLabel saveInfo = label("Your clears and best routes live in a small SQLite file on this computer.", 12, MUTED, Font.PLAIN);
        saveInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        saved.add(saveInfo);
        saved.add(Box.createVerticalStrut(8));
        recordValue.setAlignmentX(Component.LEFT_ALIGNMENT);
        saved.add(recordValue);
        sidebar.add(saved);
        return sidebar;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel tip = label("THE BEAM FOLLOWS THE RULES OF REFLECTION. FIND THE SAFE CHANNEL HOME.", 10, MUTED, Font.BOLD);
        footer.add(tip, BorderLayout.WEST);
        JButton reset = button("Restart channel", false);
        reset.addActionListener(event -> loadLevel(levelIndex));
        footer.add(reset, BorderLayout.EAST);
        return footer;
    }

    private void loadLevel(int index) {
        if (index < 0 || index >= unlocked) return;
        levelIndex = index;
        Puzzle puzzle = PUZZLES.get(index);
        mirrors.clear();
        activeMirrors.clear();
        for (Point point : puzzle.turns()) {
            activeMirrors.add(point);
            mirrors.put(point, puzzle.solution().get(point));
        }
        // Every second level starts with a few mirrors deliberately out of alignment.
        if (index % 2 == 0) {
            for (int i = 0; i < puzzle.turns().size(); i += 2) {
                Point point = puzzle.turns().get(i);
                mirrors.put(point, !mirrors.get(point));
            }
        } else {
            for (int i = 1; i < puzzle.turns().size(); i += 2) {
                Point point = puzzle.turns().get(i);
                mirrors.put(point, !mirrors.get(point));
            }
        }
        moves = 0;
        hints = 0;
        solved = false;
        trace.clear();
        titleLabel.setText(puzzle.name());
        storyLabel.setText(puzzle.story());
        levelLabel.setText(String.format("LEVEL %02d / %02d", index + 1, PUZZLES.size()));
        statusLabel.setText("Rotate the lit mirrors, then send the signal.");
        objectiveValue.setText("Reach the ship marked with the gold beacon.");
        movesValue.setText("0");
        hintsValue.setText("0");
        scoreValue.setText("—");
        bestValue.setText(bestScores.containsKey(index + 1) ? bestScores.get(index + 1) + " pts" : "Not yet");
        traceButton.setEnabled(true);
        hintButton.setEnabled(true);
        nextButton.setEnabled(false);
        levelPicker.setSelectedIndex(index);
        board.repaint();
    }

    private void rotateMirror(Point point) {
        if (solved) return;
        mirrors.put(point, !mirrors.get(point));
        moves++;
        trace.clear();
        movesValue.setText(Integer.toString(moves));
        scoreValue.setText("—");
        statusLabel.setText("Mirror turned. Send a pulse when the route feels right.");
        board.repaint();
    }

    private void revealMirror() {
        if (solved) return;
        Puzzle puzzle = PUZZLES.get(levelIndex);
        for (Point point : puzzle.turns()) {
            if (!mirrors.get(point).equals(puzzle.solution().get(point))) {
                mirrors.put(point, puzzle.solution().get(point));
                hints++;
                hintsValue.setText(Integer.toString(hints));
                statusLabel.setText("A lantern keeper aligned one mirror for you. Keep going.");
                trace.clear();
                board.repaint();
                return;
            }
        }
        statusLabel.setText("Every mirror is aligned. Send the signal!");
    }

    private void sendSignal() {
        if (solved) return;
        Puzzle puzzle = PUZZLES.get(levelIndex);
        TraceResult result = calculateTrace(puzzle);
        trace.clear();
        trace.addAll(result.path());
        if (!result.success()) {
            persistAttempt(0, false);
            scoreValue.setText("0");
            statusLabel.setText(result.message());
            board.repaint();
            return;
        }
        solved = true;
        int score = Math.max(0, 100 - moves * 7 - hints * 18);
        scoreValue.setText(score + "  ·  " + stars(score));
        persistAttempt(score, true);
        unlocked = Math.min(PUZZLES.size(), Math.max(unlocked, levelIndex + 2));
        statusLabel.setText("The harbor answered. You brought the signal home!");
        objectiveValue.setText("Safe arrival! " + stars(score) + "  ·  best route saved.");
        traceButton.setEnabled(false);
        hintButton.setEnabled(false);
        levelPicker.repaint();
        nextButton.setEnabled(levelIndex + 1 < unlocked);
        board.repaint();
    }

    private TraceResult calculateTrace(Puzzle puzzle) {
        List<Point> path = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Point current = new Point(puzzle.source());
        Direction direction = Direction.EAST;
        for (int step = 0; step < 80; step++) {
            path.add(new Point(current));
            if (current.equals(puzzle.dock())) return new TraceResult(true, path, "");
            String state = current.x + ":" + current.y + ":" + direction;
            if (!visited.add(state)) return new TraceResult(false, path, "The light looped back on itself. Try another angle.");
            Boolean slash = mirrors.get(current);
            if (slash != null) direction = direction.reflect(slash);
            Point next = new Point(current.x + direction.dr, current.y + direction.dc);
            if (next.x < 0 || next.x >= 7 || next.y < 0 || next.y >= 7)
                return new TraceResult(false, path, "The pulse slipped beyond the chart. Turn a mirror and retry.");
            if (puzzle.reefs().contains(next)) {
                path.add(next);
                return new TraceResult(false, path, "Reef! The beam struck stone. Find a safer passage.");
            }
            current = next;
        }
        return new TraceResult(false, path, "The pulse lost its way. Try a different reflection.");
    }

    private void persistAttempt(int score, boolean won) {
        try {
            store.saveAttempt(levelIndex + 1, score, hints, moves, won);
            recordValue.setText(store.totalAttempts() + " tries saved locally");
            if (won) {
                bestScores.put(levelIndex + 1, store.loadBestScores().get(levelIndex + 1));
                bestValue.setText(bestScores.get(levelIndex + 1) + " pts");
            }
        } catch (SQLException error) {
            statusLabel.setText("Could not save this attempt: " + error.getMessage());
        }
    }

    private static String stars(int score) {
        int count = score >= 86 ? 3 : score >= 65 ? 2 : 1;
        return "★".repeat(count) + "☆".repeat(3 - count);
    }

    private static JPanel card() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(46, 71, 82), 1, true),
            new EmptyBorder(15, 16, 15, 16)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    private static JPanel statBlock(String name, JLabel value) {
        JPanel block = new JPanel();
        block.setOpaque(false);
        block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
        JLabel caption = label(name, 9, MUTED, Font.BOLD);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(caption);
        block.add(Box.createVerticalStrut(4));
        block.add(value);
        return block;
    }

    private static JLabel label(String text, int size, Color color, int style) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, style, size));
        label.setForeground(color);
        return label;
    }

    private static JButton button(String text, boolean primary) {
        JButton button = new JButton(text);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, primary ? 13 : 11));
        button.setForeground(primary ? NIGHT : TEXT);
        button.setBackground(primary ? GOLD : PANEL_LIGHT);
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(primary ? GOLD : new Color(53, 81, 91), 1, true),
            new EmptyBorder(10, 12, 10, 12)));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        button.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return button;
    }

    private final class Board extends JPanel {
        private int cell;
        private int originX;
        private int originY;

        Board() {
            setOpaque(true);
            setBackground(new Color(13, 29, 39));
            setBorder(BorderFactory.createLineBorder(new Color(39, 64, 74), 1, true));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent event) {
                    int col = (event.getX() - originX) / cell;
                    int row = (event.getY() - originY) / cell;
                    Point point = new Point(row, col);
                    if (row >= 0 && col >= 0 && row < 7 && col < 7 && activeMirrors.contains(point)) rotateMirror(point);
                }
            });
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            cell = Math.min((getWidth() - 36) / 7, (getHeight() - 36) / 7);
            originX = (getWidth() - cell * 7) / 2;
            originY = (getHeight() - cell * 7) / 2;
            Puzzle puzzle = PUZZLES.get(levelIndex);
            for (int row = 0; row < 7; row++) {
                for (int col = 0; col < 7; col++) {
                    Point point = new Point(row, col);
                    int x = originX + col * cell + 3;
                    int y = originY + row * cell + 3;
                    int size = cell - 6;
                    g.setColor((row + col) % 2 == 0 ? new Color(23, 46, 57) : new Color(20, 41, 52));
                    g.fillRoundRect(x, y, size, size, 13, 13);
                    g.setColor(new Color(41, 68, 77));
                    g.setStroke(new BasicStroke(1f));
                    g.drawRoundRect(x, y, size, size, 13, 13);
                    if (puzzle.reefs().contains(point)) drawReef(g, x, y, size);
                    if (activeMirrors.contains(point)) drawMirror(g, point, x, y, size);
                    if (point.equals(puzzle.source())) drawBeacon(g, x, y, size, "L", AQUA);
                    if (point.equals(puzzle.dock())) drawBeacon(g, x, y, size, "D", GOLD);
                }
            }
            drawTrace(g);
            g.dispose();
        }

        private void drawReef(Graphics2D g, int x, int y, int size) {
            g.setColor(new Color(54, 74, 81));
            int cx = x + size / 2;
            int cy = y + size / 2;
            int r = size / 5;
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
            g.setColor(new Color(117, 134, 132));
            g.fillOval(cx - r / 2, cy - r - 4, r, r);
            g.fillOval(cx + r / 2, cy - r / 3, r / 2 + 2, r / 2 + 2);
        }

        private void drawMirror(Graphics2D g, Point point, int x, int y, int size) {
            int cx = x + size / 2;
            int cy = y + size / 2;
            int span = size / 4;
            g.setColor(new Color(245, 193, 91, 28));
            g.fillOval(cx - size / 3, cy - size / 3, size * 2 / 3, size * 2 / 3);
            g.setColor(mirrors.get(point) ? new Color(255, 226, 154) : new Color(255, 202, 112));
            g.setStroke(new BasicStroke(6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            if (mirrors.get(point)) g.drawLine(cx - span, cy + span, cx + span, cy - span);
            else g.drawLine(cx - span, cy - span, cx + span, cy + span);
            g.setColor(GOLD);
            g.fillOval(cx - 4, cy - 4, 8, 8);
        }

        private void drawBeacon(Graphics2D g, int x, int y, int size, String letter, Color color) {
            int d = size / 2;
            int cx = x + size / 2;
            int cy = y + size / 2;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 42));
            g.fillOval(cx - d / 2 - 5, cy - d / 2 - 5, d + 10, d + 10);
            g.setColor(color);
            g.fillOval(cx - d / 2, cy - d / 2, d, d);
            g.setColor(NIGHT);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(11, size / 4)));
            int tw = g.getFontMetrics().stringWidth(letter);
            g.drawString(letter, cx - tw / 2, cy + g.getFontMetrics().getAscent() / 3);
        }

        private void drawTrace(Graphics2D g) {
            if (trace.size() < 2) return;
            g.setColor(new Color(245, 193, 91, 180));
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int i = 1; i < trace.size(); i++) {
                Point before = trace.get(i - 1);
                Point after = trace.get(i);
                int x1 = originX + before.y * cell + cell / 2;
                int y1 = originY + before.x * cell + cell / 2;
                int x2 = originX + after.y * cell + cell / 2;
                int y2 = originY + after.x * cell + cell / 2;
                g.drawLine(x1, y1, x2, y2);
            }
            for (Point point : trace) {
                int cx = originX + point.y * cell + cell / 2;
                int cy = originY + point.x * cell + cell / 2;
                g.setColor(new Color(255, 232, 172));
                g.fillOval(cx - 4, cy - 4, 8, 8);
            }
        }
    }

    private record TraceResult(boolean success, List<Point> path, String message) { }
}
