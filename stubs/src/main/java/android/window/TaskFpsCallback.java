/*
 * Compile-time stub of android.window.TaskFpsCallback (hidden framework class,
 * stable shape since Android 12). compileOnly: the real class is used at
 * runtime, so the anonymous subclass created by the app ends up extending the
 * real ITaskFpsCallback.Stub and works as a genuine binder callback.
 */
package android.window;

public abstract class TaskFpsCallback extends ITaskFpsCallback.Stub {

    public abstract void onFpsReported(float fps);

    @Override
    public void onFpsSample(float fps, long uptimeMillis) {
        onFpsReported(fps);
    }
}
