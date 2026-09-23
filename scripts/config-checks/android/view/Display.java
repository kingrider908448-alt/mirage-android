package android.view;
public final class Display {
    public static final int DEFAULT_DISPLAY = 0;
    private final int id;
    public Display(int id) { this.id = id; }
    public int getDisplayId() { return id; }
}
