package javax.microedition.lcdui;

public class TextBox extends Displayable {
    public static final int ANY = 0;
    private final String title; private String text; private final int maxSize;

    public TextBox(String title, String text, int maxSize, int constraints) {
        this.title = title; this.text = text == null ? "" : text; this.maxSize = maxSize;
    }

    public String getString() { return text; }
    public void setString(String s) { text = s; }

    void runDialog() {
        final TextBox self = this;
        rally3d.desktop.Platform.inputDialog(title, text, maxSize, new java.util.function.Consumer<String>() {
            public void accept(String result) {
                Command ok = null, cancel = null;
                for (Command c : commands) {
                    if (c.type == Command.OK && ok == null) ok = c;
                    else if (cancel == null) cancel = c;
                }
                if (result != null) text = result;
                Command chosen = result != null ? ok : cancel;
                if (chosen != null && listener != null) listener.commandAction(chosen, self);
            }
        });
    }
}
