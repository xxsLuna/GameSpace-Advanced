/*
 * Compile-time stub of android.view.InputEventReceiver (@hide abstract class).
 * The app's watcher subclasses this; at runtime the anonymous subclass
 * resolves against the real framework class, so binder-level input events
 * are delivered for real. compileOnly.
 */
package android.view;

import android.os.Looper;

public abstract class InputEventReceiver {

    public InputEventReceiver(Looper looper) {
        throw new UnsupportedOperationException("stub");
    }

    public InputEventReceiver(InputChannel inputChannel, Looper looper) {
        throw new UnsupportedOperationException("stub");
    }

    public void dispose() {
    }

    public void finishInputEvent(InputEvent event, boolean handled) {
    }

    public abstract void dispatchInputEvent(int seq, InputEvent event);
}
