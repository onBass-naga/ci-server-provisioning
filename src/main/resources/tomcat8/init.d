#!/bin/bash
#
# tomcat8
#
# chkconfig: - 80 20
#
### BEGIN INIT INFO
# Provides: tomcat8
# Required-Start: $network $syslog
# Required-Stop: $network $syslog
# Default-Start:
# Default-Stop:
# Description: Tomcat 8
# Short-Description: start and stop tomcat
### END INIT INFO

## Source function library.
#. /etc/rc.d/init.d/functions
export JAVA_HOME=/usr/java/default
export JAVA_OPTS="-Dfile.encoding=UTF-8
  -Dnet.sf.ehcache.skipUpdateCheck=true
  -XX:+UseConcMarkSweepGC
  -XX:+CMSClassUnloadingEnabled
  -XX:+UseParNewGC
  -XX:MaxPermSize=128m
  -Xms512m -Xmx1024m"
export PATH=$JAVA_HOME/bin:$PATH
TOMCAT_HOME=/opt/tomcat8/apache-tomcat-8.0.15
TOMCAT_USER=tomcat8
SHUTDOWN_WAIT=20

tomcat_pid() {
  echo `ps aux | grep org.apache.catalina.startup.Bootstrap | grep -v grep | awk '{ print $2 }'`
}

start() {
  pid=$(tomcat_pid)
  if [ -n "$pid" ]
  then
    echo "Tomcat is already running (pid: $pid)"
  else
    # Start tomcat
    echo "Starting tomcat"
    ulimit -n 100000
    umask 007
    /bin/su -p -s /bin/sh $TOMCAT_USER $TOMCAT_HOME/bin/startup.sh
  fi


  return 0
}

stop() {
  pid=$(tomcat_pid)
  if [ -n "$pid" ]
  then
    echo "Stoping Tomcat"
    /bin/su -p -s /bin/sh $TOMCAT_USER $TOMCAT_HOME/bin/shutdown.sh

    let kwait=$SHUTDOWN_WAIT
    count=0;
    until [ `ps -p $pid | grep -c $pid` = '0' ] || [ $count -gt $kwait ]
    do
      echo -n -e "\nwaiting for processes to exit";
      sleep 1
      let count=$count+1;
    done

    if [ $count -gt $kwait ]; then
      echo -n -e "\nkilling processes which didn't stop after $SHUTDOWN_WAIT seconds"
      kill -9 $pid
    fi
  else
    echo "Tomcat is not running"
  fi

  return 0
}

case $1 in
start)
  start
;;
stop)
  stop
;;
restart)
  stop
  start
;;
status)
  pid=$(tomcat_pid)
  if [ -n "$pid" ]
  then
    echo "Tomcat is running with pid: $pid"
  else
    echo "Tomcat is not running"
  fi
;;
esac
exit 0