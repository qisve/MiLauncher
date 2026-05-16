#!/system/bin/sh
SKIPUNZIP=1
ui_print "- 安装 MiLauncher v2.0"
unzip -o "$ZIPFILE" -d "$MODPATH" >&2
set_perm_recursive $MODPATH/system 0 0 0755 0644
ui_print "- 安装完成，重启生效"
