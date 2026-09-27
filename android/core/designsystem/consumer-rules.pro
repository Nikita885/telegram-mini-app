# Компоненты дизайн-системы создаются из XML по имени класса — сохраняем конструкторы.
-keep public class app.outfitshare.core.designsystem.component.** extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}
