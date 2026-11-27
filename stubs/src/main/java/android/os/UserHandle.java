/*
 * Compile-time shadow of android.os.UserHandle (hidden members only surface).
 * compileOnly: the real framework class is used at runtime.
 */
package android.os;

public final class UserHandle implements Parcelable {

    /** Hidden @hide constant, stable value. */
    public static final int USER_CURRENT = 2;
    public static final int USER_ALL = -10000;
    public static final int USER_OWNER = 0;

    /** Hidden static instance; only ever handed to hidden Context methods. */
    public static final UserHandle CURRENT = new UserHandle(-2);
    public static final UserHandle ALL = new UserHandle(-10000);

    private final int mHandle;

    private UserHandle(int handle) {
        mHandle = handle;
    }

    public int getIdentifier() {
        return mHandle;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof UserHandle && ((UserHandle) obj).mHandle == mHandle;
    }

    @Override
    public int hashCode() {
        return mHandle;
    }
}
