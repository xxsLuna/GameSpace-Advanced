# GameSpace Burn-In Guard installer (Magisk / KernelSU / APatch).
# The zip ships a system_ext/ staging tree; this script relocates it to the
# *physical* location the ROM actually keeps GameSpace in, so Magisk's magic
# mount binds the real file (important on devices where system_ext is merged
# into the system image and /system_ext is only a symlink: staging by symlink
# path leaves the old APK mounted).
ui_print "- GameSpace Burn-In Guard"

PKG="io.chaldeaprjkt.gamespace"
REL="/priv-app/GameSpace"

# The zip always stages under system_ext; the target is that staging moved.
STAGED="$MODPATH/system_ext"

# Candidate target roots (relative to $MODPATH once relocated), most specific
# first. The live install path from pm is physical (symlinks resolved), which
# is exactly what magic mount needs.
TARGET=""
INSTALLED="$(pm path "$PKG" 2>/dev/null | head -n1 | cut -d: -f2 | tr -d '\r\n')"
case "$INSTALLED" in
  *"$REL"/*) TARGET="${INSTALLED%"$REL"/*}" ;;
esac
if [ -z "$TARGET" ]; then
  for CAND in /system/system_ext /system_ext /product /system/product /system /vendor/product; do
    if [ -d "$CAND$REL" ]; then
      TARGET="${CAND#/}"
      break
    fi
  done
fi
if [ -z "$TARGET" ]; then
  TARGET="system_ext"   # fresh layout default
fi
# Normalize (strip leading slash, no trailing slash)
TARGET="${TARGET#/}"
TARGET="${TARGET%/}"
ui_print "- Target: $TARGET"

if [ "$TARGET" != "system_ext" ]; then
  mkdir -p "$MODPATH/$TARGET$REL" "$MODPATH/$TARGET/etc/permissions"
  mv "$STAGED$REL/GameSpace.apk" "$MODPATH/$TARGET$REL/"
  mv "$STAGED/etc/permissions/privapp-permissions-$PKG.xml" \
     "$MODPATH/$TARGET/etc/permissions/"
  rm -rf "$STAGED"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644 u:object_r:system_file:s0

ui_print "- Installed. Reboot to take effect."
ui_print "- If the device bootloops, remove /data/adb/modules/gamespace_burnin_guard"
ui_print "  (safe mode also disables all modules)."
