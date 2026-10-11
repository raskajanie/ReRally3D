package rally3d.desktop;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Hashtable;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Modal display settings: FPS slider, resolution slider, fullscreen + FPS-counter checkboxes. */
final class SettingsDialog {
    private SettingsDialog() {}

    static String fpsLabel(int idx) {
        int f = Platform.FPS_VALUES[idx];
        return f == 0 ? "Unlimited" : f + " FPS";
    }

    static String resLabel(int idx) { return Platform.RESOLUTIONS[idx][0] + " x " + Platform.RESOLUTIONS[idx][1]; }

    /** @return true if the user pressed OK (values are already stored in Platform). */
    static boolean show(JFrame owner) {
        final JDialog d = new JDialog(owner, "Rally 3D - Display settings", true);
        final boolean[] ok = new boolean[1];

        final JSlider fps = new JSlider(0, Platform.FPS_VALUES.length - 1, Platform.fpsIndex);
        final JLabel fpsText = new JLabel(fpsLabel(Platform.fpsIndex));
        fps.setMajorTickSpacing(1); fps.setPaintTicks(true); fps.setSnapToTicks(true);
        Hashtable<Integer, JLabel> fl = new Hashtable<Integer, JLabel>();
        for (int i = 0; i < Platform.FPS_VALUES.length; i++) {
            int f = Platform.FPS_VALUES[i];
            fl.put(i, new JLabel(f == 0 ? "\u221e" : String.valueOf(f)));
        }
        fps.setLabelTable(fl); fps.setPaintLabels(true);
        fps.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) { fpsText.setText(fpsLabel(fps.getValue())); }
        });

        final JSlider res = new JSlider(0, Platform.RESOLUTIONS.length - 1, Platform.resIndex);
        final JLabel resText = new JLabel(resLabel(Platform.resIndex));
        res.setMajorTickSpacing(1); res.setPaintTicks(true); res.setSnapToTicks(true);
        Hashtable<Integer, JLabel> rl = new Hashtable<Integer, JLabel>();
        rl.put(0, new JLabel(resLabel(0)));
        rl.put(Platform.RESOLUTIONS.length - 1, new JLabel(resLabel(Platform.RESOLUTIONS.length - 1)));
        res.setLabelTable(rl); res.setPaintLabels(true);
        res.addChangeListener(new ChangeListener() {
            public void stateChanged(ChangeEvent e) { resText.setText(resLabel(res.getValue())); }
        });

        final JCheckBox fs = new JCheckBox("Fullscreen window (borderless; F11 / Alt+Enter)", Platform.fullscreen);
        final JCheckBox sf = new JCheckBox("Show FPS counter in race", Platform.showFps);

        JPanel body = new JPanel(new GridLayout(0, 1, 0, 4));
        body.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        body.add(row("Frame rate limit:", fpsText));
        body.add(fps);
        body.add(row("Render resolution:", resText));
        body.add(res);
        body.add(fs);
        body.add(sf);

        JButton okB = new JButton("OK");
        JButton cancelB = new JButton("Cancel");
        okB.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Platform.configure(res.getValue(), fps.getValue(), fs.isSelected(), sf.isSelected());
                ok[0] = true; d.dispose();
            }
        });
        cancelB.addActionListener(new ActionListener() { public void actionPerformed(ActionEvent e) { d.dispose(); } });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(okB); buttons.add(cancelB);

        d.setLayout(new BorderLayout());
        d.add(body, BorderLayout.CENTER);
        d.add(buttons, BorderLayout.SOUTH);
        d.getRootPane().setDefaultButton(okB);
        d.setPreferredSize(new Dimension(520, 330));
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return ok[0];
    }

    private static JPanel row(String name, JLabel value) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(name), BorderLayout.WEST);
        value.setFont(value.getFont().deriveFont(java.awt.Font.BOLD));
        p.add(value, BorderLayout.EAST);
        return p;
    }
}
