import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;
import javax.swing.*;

/** Disposable input probe. No IntelliJ, product services, CLI, or settings access. */
public final class SwingCuaFixture {
    private static void event(String kind, String value) {
        System.out.println(Instant.now() + " " + kind + " "
                + value.replace("\n", "\\n").replace("\r", "\\r"));
        System.out.flush();
    }

    public static void main(String[] args) throws Exception {
        Properties identity = new Properties();
        try (var stream = SwingCuaFixture.class.getResourceAsStream("/identity.properties")) {
            if (stream == null) throw new IllegalStateException("Missing build identity");
            identity.load(stream);
        }
        Path jar = Path.of(SwingCuaFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(jar)));
        String boot = UUID.randomUUID().toString().substring(0, 8);
        String marker = identity.getProperty("run") + " / " + identity.getProperty("head").substring(0, 12)
                + " / jar " + hash.substring(0, 12) + " / boot " + boot;
        event("BOOT", marker + " full-jar=" + hash);
        SwingUtilities.invokeLater(() -> createWindow(marker));
    }

    private static void createWindow(String marker) {
        JFrame frame = new JFrame("SWING-CUA / " + marker);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(new JLabel("SWING-CUA / " + marker));
        top.add(new JLabel("使い捨て入力検証 / CLI送信なし / 製品UIの検証対象外"));
        JLabel status = new JLabel("操作結果: 待機");
        JTextField input = new JTextField(26);
        input.getAccessibleContext().setAccessibleName("検証入力");
        Runnable commit = () -> {
            status.setText("確定: " + input.getText());
            event("COMMIT", input.getText());
        };
        input.addActionListener(e -> commit.run());
        input.addInputMethodListener(new InputMethodListener() {
            public void inputMethodTextChanged(InputMethodEvent e) {
                event("IME", "committed=" + e.getCommittedCharacterCount());
            }
            public void caretPositionChanged(InputMethodEvent e) { }
        });
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT));
        controls.add(new JLabel("入力:"));
        controls.add(input);
        JButton apply = new JButton("確定");
        apply.addActionListener(e -> commit.run());
        controls.add(apply);
        int[] count = {0};
        JButton click = new JButton("クリック");
        click.addActionListener(e -> {
            status.setText("クリック: " + ++count[0]);
            event("CLICK", Integer.toString(count[0]));
        });
        controls.add(click);
        top.add(controls);
        JPanel extras = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton popup = new JButton("Popup");
        JPopupMenu menu = new JPopupMenu();
        for (String label : new String[]{"選択 A", "選択 B"}) {
            JMenuItem item = new JMenuItem(label);
            item.addActionListener(e -> {
                status.setText("Popup: " + label);
                event("POPUP_SELECT", label);
            });
            menu.add(item);
        }
        popup.addActionListener(e -> {
            event("POPUP_OPEN", "requested");
            menu.show(popup, 0, popup.getHeight());
        });
        extras.add(popup);
        JButton dialog = new JButton("Dialog");
        dialog.addActionListener(e -> {
            JDialog child = new JDialog(frame, "SWING-CUA DIALOG", true);
            JTextField field = new JTextField(20);
            field.getAccessibleContext().setAccessibleName("ダイアログ入力");
            JButton done = new JButton("ダイアログ確定");
            done.addActionListener(a -> {
                status.setText("Dialog: " + field.getText());
                event("DIALOG_COMMIT", field.getText());
                child.dispose();
            });
            JPanel panel = new JPanel(new FlowLayout());
            panel.add(field);
            panel.add(done);
            child.add(panel);
            child.pack();
            child.setLocationRelativeTo(frame);
            event("DIALOG_OPEN", "requested");
            child.setVisible(true);
        });
        extras.add(dialog);
        JTextField dragSource = new JTextField("DRAG-ME", 8);
        dragSource.setDragEnabled(true);
        dragSource.getAccessibleContext().setAccessibleName("ドラッグ元");
        JTextField dropTarget = new JTextField(10);
        dropTarget.getAccessibleContext().setAccessibleName("ドロップ先");
        extras.add(dragSource);
        extras.add(dropTarget);
        top.add(extras);
        root.add(top, BorderLayout.NORTH);
        DefaultListModel<String> model = new DefaultListModel<>();
        for (int i = 1; i <= 100; i++) model.addElement(String.format("行 %03d / scroll probe", i));
        JList<String> list = new JList<>(model);
        list.setFixedCellHeight(26);
        list.getAccessibleContext().setAccessibleName("スクロール検証一覧");
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                status.setText("選択: " + list.getSelectedValue());
                event("ROW_SELECT", String.valueOf(list.getSelectedValue()));
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().addAdjustmentListener(e -> event("SCROLL", Integer.toString(e.getValue())));
        root.add(scroll, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(760, 400));
        frame.setSize(880, 560);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        event("READY", marker);
    }
}
