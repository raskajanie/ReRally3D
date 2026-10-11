package javax.microedition.lcdui;

import java.util.ArrayList;
import java.util.List;

public abstract class Displayable {
    final List<Command> commands = new ArrayList<Command>();
    CommandListener listener;

    public void addCommand(Command cmd) { commands.add(cmd); }
    public void removeCommand(Command cmd) { commands.remove(cmd); }
    public void setCommandListener(CommandListener l) { listener = l; }
    public boolean isShown() { return Display.current == this; }
    public int getWidth() { return rally3d.desktop.Platform.logicalWidth(); }
    public int getHeight() { return rally3d.desktop.Platform.logicalHeight(); }
}
