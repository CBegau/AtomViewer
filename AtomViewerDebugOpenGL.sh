#!/bin/sh

script_dir=$(dirname $0)

if [ $# -eq 0 ];
then
  cd $script_dir
fi

java -jar -Djogl.debug.DebugGL -Xmx3000M $script_dir/AtomViewer.jar $@
