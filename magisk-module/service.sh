#!/system/bin/sh
# One-time (and self-healing) bootstrap: on ROMs whose platform key is not the
# AOSP testkey, PMS rejects the replaced system package once (old record has
# the ROM signature + shared user), wipes the record, and needs a second boot
# to accept the new signature. Installing the module APK explicitly through pm
# removes that chicken-and-egg cycle: after a plain pm install the package is
# simply an updated app with our signature.
MODDIR=${0%/*}

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done
sleep 5

APK=$(find "$MODDIR" -name GameSpace.apk 2>/dev/null | head -n1)
[ -z "$APK" ] && exit

# Our build reports versionCode 999999 and a live package. A stale record can
# exist with the parsed package unloaded (pkg=null), so pm path must answer too.
if dumpsys package io.chaldeaprjkt.gamespace | grep -q "versionCode=999999" &&
   pm path io.chaldeaprjkt.gamespace >/dev/null 2>&1 &&
   dumpsys package io.chaldeaprjkt.gamespace | grep -q "pkg=Package"; then
    :
else
    # Never install straight from the Magisk mount: PMS may record an
    # incremental (incfs) install that breaks on the next boot. Stage first.
    cp "$APK" /data/local/tmp/gamespace_burnin.apk
    chmod 644 /data/local/tmp/gamespace_burnin.apk
    pm install -r /data/local/tmp/gamespace_burnin.apk >/dev/null 2>&1 ||
        pm install /data/local/tmp/gamespace_burnin.apk >/dev/null 2>&1
    rm -f /data/local/tmp/gamespace_burnin.apk
fi

# Without android.uid.system the permissions the shared uid used to imply do
# not come for free: the two dangerous ones need an explicit grant and the
# overlay (game bar, burn-in window) is appop-managed. All idempotent.
pm grant io.chaldeaprjkt.gamespace android.permission.READ_PHONE_STATE >/dev/null 2>&1
pm grant io.chaldeaprjkt.gamespace android.permission.ANSWER_PHONE_CALLS >/dev/null 2>&1
appops set io.chaldeaprjkt.gamespace SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1

# The game-session service asks for su with no activity on screen, which
# Magisk's prompt cannot reach behind a fullscreen game: pre-author the
# package in Magisk's policy store instead. KernelSU users approve it in the
# KernelSU app once; everything degrades to plain overlay mode without root.
UID=$(pm list packages -U io.chaldeaprjkt.gamespace 2>/dev/null |
        sed -n 's/^package:io.chaldeaprjkt.gamespace uid:\([0-9]*\)$/\1/p')
if [ -n "$UID" ] && magisk -v >/dev/null 2>&1; then
    # Magisk 27+: policies(uid, policy, until, ...) with policy 1 = allow.
    magisk --sqlite "INSERT OR REPLACE INTO policies (uid,policy,until,logging,notification) VALUES($UID,1,0,1,1)" >/dev/null 2>&1
fi
