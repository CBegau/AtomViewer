#!/bin/sh

script_dir=$(dirname $0)

if [ $# -eq 0 ];
then
  cd $script_dir
  script_dir="."
fi

java -jar -Xmx3000M -server $script_dir/AtomViewer.jar $@


