#! /bin/sh
#
# Columnal: Safer, smoother data table processing.
# Copyright (c) Neil Brown, 2016-2020, 2022.
#
# This file is part of Columnal.
#
# Columnal is free software: you can redistribute it and/or modify it under
# the terms of the GNU General Public License as published by the Free
# Software Foundation, either version 3 of the License, or (at your option)
# any later version.
#
# Columnal is distributed in the hope that it will be useful, but WITHOUT 
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
# FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for 
# more details.
#
# You should have received a copy of the GNU General Public License along 
# with Columnal. If not, see <https://www.gnu.org/licenses/>.
#

Xvfb :42 \
  -screen 0 1920x1200x24 \
  -screen 1 1920x1200x24 \
  -screen 2 1920x1200x24 \
  -screen 3 1920x1200x24 \
  -screen 4 1920x1200x24 \
  -screen 5 1920x1200x24 \
  -screen 6 1920x1200x24 \
  -screen 7 1920x1200x24 \
  -screen 8 1920x1200x24 \
  -screen 9 1920x1200x24 \
  -screen 10 1920x1200x24 \
  -screen 11 1920x1200x24 \
  -screen 12 1920x1200x24 \
  -screen 13 1920x1200x24 \
  -screen 14 1920x1200x24 \
  -screen 15 1920x1200x24 \
  &

sleep 5

DISPLAY=:42.0 icewm &
DISPLAY=:42.1 icewm &
DISPLAY=:42.2 icewm &
DISPLAY=:42.3 icewm &
DISPLAY=:42.4 icewm &
DISPLAY=:42.5 icewm &
DISPLAY=:42.6 icewm &
DISPLAY=:42.7 icewm &
DISPLAY=:42.8 icewm &
DISPLAY=:42.9 icewm &
DISPLAY=:42.10 icewm &
DISPLAY=:42.11 icewm &
DISPLAY=:42.12 icewm &
DISPLAY=:42.13 icewm &
DISPLAY=:42.14 icewm &
DISPLAY=:42.15 icewm &

xclock -display :42.0 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.1 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.2 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.3 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.4 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.5 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.6 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.7 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.8 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.9 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.10 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.11 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.12 -digital -update 1 -strftime ':42.0 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.13 -digital -update 1 -strftime ':42.1 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.14 -digital -update 1 -strftime ':42.2 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
xclock -display :42.15 -digital -update 1 -strftime ':42.3 %Y-%m-%d %H:%M:%S' -geometry +0+0 -fg white -bg black &
