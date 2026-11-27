/*
 * Compile-time stub of android.window.ITaskFpsCallback (AIDL-generated in the
 * real framework). Only the shape matters: the app's anonymous TaskFpsCallback
 * subclass resolves its superclass against the real framework classes at
 * runtime. compileOnly: never packaged.
 */
package android.window;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;

public interface ITaskFpsCallback extends IInterface {

    void onFpsSample(float fps, long uptimeMillis) throws RemoteException;

    abstract class Stub extends Binder implements ITaskFpsCallback {

        public static final String DESCRIPTOR = "android.window.ITaskFpsCallback";

        public static final int TRANSACTION_onFpsSample = IBinder.FIRST_CALL_TRANSACTION;

        public Stub() {
        }

        public static ITaskFpsCallback asInterface(IBinder obj) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        public IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            return true;
        }
    }
}
